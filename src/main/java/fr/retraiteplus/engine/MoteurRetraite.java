package fr.retraiteplus.engine;

import fr.retraiteplus.engine.LigneCarriere.Devise;
import fr.retraiteplus.engine.Parametres.Montant;
import fr.retraiteplus.engine.RegleGeneration.Age;
import fr.retraiteplus.engine.RegleGeneration.BorneCarriereLongue;
import fr.retraiteplus.engine.ResultatSimulation.AnneeDetail;
import fr.retraiteplus.engine.ResultatSimulation.Scenario;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Moteur de calcul de la retraite d'un salarié du privé :
 * retraite de base (Assurance retraite / régime général) + complémentaire Agirc-Arrco.
 * <p>
 * Chaîne de calcul :
 * <ol>
 *   <li>règles de la génération (âge légal, durée requise, carrière longue) ;</li>
 *   <li>carrière année par année : trimestres cotisés / assimilés, contrôle des seuils, points ;</li>
 *   <li>conditions de départ (âge légal ou carrière longue) ;</li>
 *   <li>SAM : 25 meilleures années, plafonnées au PASS, converties en euros, revalorisées ;</li>
 *   <li>taux : taux plein, décote ou surcote ; proratisation ;</li>
 *   <li>pension de base (plafond 50 % PASS, majoration enfants, minimum contributif) ;</li>
 *   <li>Agirc-Arrco : points × valeur de service (coefficient d'anticipation éventuel) ;</li>
 *   <li>net, montant revalorisé à ce jour, réversion, comparaison de dates de départ.</li>
 * </ol>
 */
public class MoteurRetraite {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final BigDecimal DOUZE = BigDecimal.valueOf(12);

    private final Parametres p;

    public MoteurRetraite(Parametres p) { this.p = p; }

    public ResultatSimulation simuler(DonneesSimulation d) {
        ResultatSimulation r = calculer(d);
        r.scenarios = comparerDates(d, r);
        return r;
    }

    // =====================================================================================
    ResultatSimulation calculer(DonneesSimulation d) {
        if (d.dateNaissance == null) throw new IllegalArgumentException("La date de naissance est obligatoire.");
        if (d.dateDepart == null) throw new IllegalArgumentException("La date de départ est obligatoire.");
        if (!d.dateDepart.isAfter(d.dateNaissance.plusYears(50)))
            throw new IllegalArgumentException("La date de départ doit être postérieure aux 50 ans de l'assuré.");

        ResultatSimulation r = new ResultatSimulation();
        LocalDate naissance = d.dateNaissance;
        LocalDate depart = d.dateDepart;
        int anneeDepart = depart.getYear();
        r.nomComplet = (d.prenom + " " + d.nom).trim();
        r.dateNaissance = naissance;
        r.dateDepart = depart;
        r.dateCalcul = d.dateCalcul;
        r.ageAuDepart = Age.entre(naissance, depart).libelle();
        if (depart.getDayOfMonth() != 1)
            r.alertes.add("Une retraite prend effet le 1er jour d'un mois : la date de départ choisie ("
                    + date(depart) + ") n'est pas un 1er du mois.");

        // ---------- 1. Règles de la génération ----------
        RegleGeneration regle = RegleGeneration.pour(naissance, depart);
        r.ageLegal = regle.getAgeLegal().libelle();
        r.dateAgeLegal = regle.getAgeLegal().atteintLe(naissance);
        r.ageTauxPlein = regle.getAgeTauxPlein().libelle();
        r.dateAgeTauxPlein = regle.getAgeTauxPlein().atteintLe(naissance);
        r.dureeRequise = regle.getDureeRequise();
        r.suspensionAppliquee = regle.isSuspensionAppliquee();
        r.etapes.add("Génération " + naissance.getYear() + " : âge légal " + r.ageLegal + " (atteint le "
                + date(r.dateAgeLegal) + "), durée requise " + r.dureeRequise + " trimestres, taux plein automatique à "
                + r.ageTauxPlein + "." + (r.suspensionAppliquee ? " Valeurs issues de la suspension de la réforme (LFSS 2026)." : ""));

        // ---------- 2. Carrière année par année ----------
        List<LigneCarriere> lignes = new ArrayList<>(d.carriere);
        ajouterProjection(d, lignes, r);
        TreeMap<Integer, List<LigneCarriere>> parAnnee = new TreeMap<>();
        for (LigneCarriere l : lignes) {
            if (l.annee() > anneeDepart) {
                r.alertes.add("Ligne " + l.annee() + " ignorée : elle est postérieure à l'année de départ.");
                continue;
            }
            parAnnee.computeIfAbsent(l.annee(), k -> new ArrayList<>()).add(l);
        }
        if (parAnnee.isEmpty()) r.alertes.add("Aucune ligne de carrière : la pension calculée sera nulle.");

        Map<TypePeriode, Integer> assimilesParType = new EnumMap<>(TypePeriode.class);
        TreeMap<Integer, Integer> reputesCotisesParAnnee = new TreeMap<>(); // pour la condition de début de carrière
        boolean alerteCoef = false;
        BigDecimal pointsDeclares = BigDecimal.ZERO, pointsCalcules = BigDecimal.ZERO;
        BigDecimal dernierSalaire = null;

        for (Map.Entry<Integer, List<LigneCarriere>> e : parAnnee.entrySet()) {
            int annee = e.getKey();
            AnneeDetail a = new AnneeDetail();
            a.annee = annee;
            Montant pass = p.pass(annee);
            Devise devAnnee = pass.devise();
            a.devise = devAnnee.name();
            a.plafond = pass.valeur();

            BigDecimal revenu = BigDecimal.ZERO;
            boolean auMoinsUnRevenu = false, trimestresDeclares = false;
            int sommeDeclares = 0;
            BigDecimal pointsAnnee = BigDecimal.ZERO;
            boolean pointsAuto = false, pointsManquants = false;

            for (LigneCarriere l : e.getValue()) {
                if (l.projetee()) a.projetee = true;
                if (l.type() != TypePeriode.SALAIRE) continue;
                BigDecimal rev = l.revenu() == null ? BigDecimal.ZERO : convertir(l.revenu(), l.devise(), devAnnee);
                if (l.revenu() != null) auMoinsUnRevenu = true;
                revenu = revenu.add(rev);
                if (l.trimestres() != null) { trimestresDeclares = true; sommeDeclares += l.trimestres(); }
                if (l.points() != null) {
                    pointsAnnee = pointsAnnee.add(l.points());
                    pointsDeclares = pointsDeclares.add(l.points());
                } else if (rev.signum() > 0) {
                    BigDecimal pts = calculerPoints(annee, rev, devAnnee, l.projetee());
                    if (pts == null) pointsManquants = true;
                    else { pointsAnnee = pointsAnnee.add(pts); pointsCalcules = pointsCalcules.add(pts); pointsAuto = true; }
                }
            }
            a.revenu = auMoinsUnRevenu ? revenu.setScale(2, RoundingMode.HALF_UP) : null;
            a.points = pointsAnnee.setScale(2, RoundingMode.HALF_UP);
            a.pointsCalcules = pointsAuto;

            // Trimestres cotisés : relevé si renseigné, sinon calcul revenu / seuil
            if (auMoinsUnRevenu && (p.seuilConnu(annee) || a.projetee)) {
                Montant seuil = p.seuilTrimestre(annee);
                BigDecimal s = convertir(seuil.valeur(), seuil.devise(), devAnnee);
                a.trimestresCalcules = Math.min(4, revenu.divide(s, 0, RoundingMode.FLOOR).intValue());
            }
            int cotises = trimestresDeclares ? sommeDeclares : (a.trimestresCalcules != null ? a.trimestresCalcules : 0);
            a.trimestresCotises = Math.min(4, cotises);
            if (trimestresDeclares && a.trimestresCalcules != null && a.trimestresCalcules != a.trimestresCotises) {
                if (a.trimestresCalcules > a.trimestresCotises)
                    a.alerte = "Le revenu de l'année permet de valider " + a.trimestresCalcules + " trimestre(s) (seuil "
                            + argent(convertir(p.seuilTrimestre(annee).valeur(), p.seuilTrimestre(annee).devise(), devAnnee))
                            + " " + devAnnee + " par trimestre) mais le relevé n'en indique que " + a.trimestresCotises
                            + " : anomalie possible, à faire régulariser auprès de la caisse.";
                else
                    a.alerte = "Le relevé indique " + a.trimestresCotises + " trimestre(s) alors que le revenu n'en valide que "
                            + a.trimestresCalcules + " : vérifier s'il s'agit de périodes assimilées.";
                r.alertes.add(annee + " : " + a.alerte);
            }
            if (pointsManquants && annee < anneeDepart)
                r.alertes.add(annee + " : points Agirc-Arrco non renseignés et non calculables (valeur d'achat du point inconnue) ; comptés pour 0.");

            // Périodes assimilées, dans la limite de 4 trimestres par an
            int reste = 4 - a.trimestresCotises;
            int reputes = 0;
            StringBuilder det = new StringBuilder();
            for (LigneCarriere l : e.getValue()) {
                if (l.type() == TypePeriode.SALAIRE || l.trimestres() == null) continue;
                int pris = Math.min(reste, l.trimestres());
                if (pris <= 0) continue;
                reste -= pris;
                a.trimestresAssimiles += pris;
                assimilesParType.merge(l.type(), pris, Integer::sum);
                if (l.type().getPlafondReputeCotise() > 0) reputes += pris;
                if (det.length() > 0) det.append(", ");
                det.append(pris).append(" ").append(l.type().getLibelle().toLowerCase());
            }
            a.detailAssimiles = det.toString();
            a.trimestresValides = a.trimestresCotises + a.trimestresAssimiles;
            reputesCotisesParAnnee.put(annee, a.trimestresCotises + reputes);

            // Revenu retenu pour le SAM
            if (a.revenu != null) {
                BigDecimal retenu = revenu.min(pass.valeur());
                a.plafonne = revenu.compareTo(pass.valeur()) > 0;
                a.revenuRetenuEur = convertir(retenu, devAnnee, Devise.EUR).setScale(2, RoundingMode.HALF_UP);
                a.exclueSam = annee >= anneeDepart;
                a.coefficient = p.coefficientSalaire(anneeDepart, annee);
                if (!a.exclueSam && !p.coefficientSalaireConnu(anneeDepart, annee)) alerteCoef = true;
                a.revenuRevalorise = a.revenuRetenuEur.multiply(a.coefficient).setScale(2, RoundingMode.HALF_UP);
                if (annee < anneeDepart && a.trimestresCotises == 4) dernierSalaire = convertir(revenu, devAnnee, Devise.EUR);
            }
            r.annees.add(a);
        }
        if (alerteCoef)
            r.alertes.add("Coefficients de revalorisation des salaires non publiés pour certaines années (coefficient 1 appliqué) : "
                    + "le SAM est un minimum, la table officielle de l'année " + anneeDepart + " sera plus favorable.");
        if (!p.tableCoefficientsExacte(anneeDepart))
            r.alertes.add("Table de coefficients " + anneeDepart + " non disponible : table " + p.anneeTableCoefficients(anneeDepart) + " utilisée.");
        r.anneeTableCoefficients = p.anneeTableCoefficients(anneeDepart);

        // ---------- 3. Totaux de trimestres ----------
        for (AnneeDetail a : r.annees) { r.trimestresCotises += a.trimestresCotises; r.trimestresAssimiles += a.trimestresAssimiles; }
        r.trimestresRegimeGeneral = r.trimestresCotises + r.trimestresAssimiles;
        r.trimestresAutresRegimes = Math.max(0, d.trimestresAutresRegimes);
        r.trimestresMajorationEnfants = d.trimestresMajorationEnfants != null ? Math.max(0, d.trimestresMajorationEnfants)
                : (d.sexe == DonneesSimulation.Sexe.FEMME ? 8 * d.nombreEnfants : 0);
        r.trimestresTousRegimes = r.trimestresRegimeGeneral + r.trimestresAutresRegimes + r.trimestresMajorationEnfants;
        r.trimestresManquants = Math.max(0, r.dureeRequise - r.trimestresTousRegimes);

        int reputes = r.trimestresCotises + r.trimestresAutresRegimes + Math.min(2, r.trimestresMajorationEnfants);
        for (Map.Entry<TypePeriode, Integer> e : assimilesParType.entrySet())
            reputes += Math.min(e.getKey().getPlafondReputeCotise(), e.getValue());
        r.trimestresReputesCotises = reputes;

        r.etapes.add("Trimestres : " + r.trimestresCotises + " cotisés + " + r.trimestresAssimiles + " assimilés au régime général"
                + (r.trimestresAutresRegimes > 0 ? " + " + r.trimestresAutresRegimes + " autres régimes" : "")
                + (r.trimestresMajorationEnfants > 0 ? " + " + r.trimestresMajorationEnfants + " de majoration enfants" : "")
                + " = " + r.trimestresTousRegimes + " trimestres tous régimes (requis : " + r.dureeRequise + ").");
        if (d.sexe == DonneesSimulation.Sexe.HOMME && d.nombreEnfants > 0 && d.trimestresMajorationEnfants == null)
            r.alertes.add("Majoration de durée pour enfants : pour un père, les 4 trimestres d'éducation par enfant ne sont accordés "
                    + "qu'en cas de partage ou s'il a élevé seul l'enfant (enfant né avant 2010 : attribués à la mère par défaut). "
                    + "Renseignez le champ si ces trimestres ont été attribués.");

        // ---------- 4. Carrière longue et conditions de départ ----------
        evaluerCarriereLongue(d, regle, reputesCotisesParAnnee, r);
        boolean clAppliquee = r.carriereLongueEligible && depart.isBefore(r.dateAgeLegal);
        if (!depart.isBefore(r.dateAgeLegal)) {
            r.departPossible = true;
            r.motifDepart = "Départ à partir de l'âge légal (" + r.ageLegal + ").";
        } else if (r.carriereLongueEligible) {
            r.departPossible = true;
            r.motifDepart = "Départ anticipé au titre de la carrière longue (" + r.carriereLongueDetail + ").";
        } else {
            r.departPossible = false;
            r.motifDepart = "Départ impossible le " + date(depart) + " : l'âge légal (" + r.ageLegal + ") n'est atteint que le "
                    + date(r.dateAgeLegal) + (r.dateCarriereLongue != null
                    ? " et les conditions de la carrière longue ne sont pas réunies à cette date." : " et aucune condition de carrière longue n'est remplie.");
            r.alertes.add(0, r.motifDepart);
        }
        r.etapes.add(r.motifDepart);

        // ---------- 5. SAM ----------
        int gen = naissance.getYear();
        r.nombreAnneesSam = gen <= 1933 ? 10 : (gen <= 1947 ? gen - 1923 : 25);
        List<AnneeDetail> eligibles = new ArrayList<>();
        for (AnneeDetail a : r.annees)
            if (a.revenuRevalorise != null && !a.exclueSam && a.revenuRevalorise.signum() > 0) eligibles.add(a);
        eligibles.sort(Comparator.comparing((AnneeDetail a) -> a.revenuRevalorise).reversed());
        BigDecimal somme = BigDecimal.ZERO;
        int n = Math.min(r.nombreAnneesSam, eligibles.size());
        for (int i = 0; i < n; i++) { eligibles.get(i).retenuPourSam = true; somme = somme.add(eligibles.get(i).revenuRevalorise); }
        r.anneesRetenues = n;
        r.sam = n == 0 ? BigDecimal.ZERO : somme.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        r.etapes.add("SAM : moyenne des " + n + " meilleures années (sur " + r.nombreAnneesSam + " prévues), salaires plafonnés au PASS, "
                + "convertis en euros (1 € = 6,55957 F) et revalorisés avec les coefficients Cnav " + r.anneeTableCoefficients
                + " = " + argent(r.sam) + " €. L'année de départ (" + anneeDepart + ") n'est pas prise en compte.");

        // ---------- 6. Taux ----------
        BigDecimal tauxPleinVal = p.constante("taux.plein");
        r.trimestresDecote = 0;
        r.trimestresSurcote = 0;
        if (d.tauxPleinParStatut) { r.tauxPlein = true; r.motifTauxPlein = "taux plein par statut (inaptitude, invalidité, handicap…)"; }
        else if (clAppliquee) { r.tauxPlein = true; r.motifTauxPlein = "retraite anticipée carrière longue (taux plein de droit)"; }
        else if (!depart.isBefore(r.dateAgeTauxPlein)) { r.tauxPlein = true; r.motifTauxPlein = "âge du taux plein automatique atteint (" + r.ageTauxPlein + ")"; }
        else if (r.trimestresTousRegimes >= r.dureeRequise) { r.tauxPlein = true; r.motifTauxPlein = "durée d'assurance requise atteinte"; }
        else {
            r.tauxPlein = false;
            int manqDuree = r.dureeRequise - r.trimestresTousRegimes;
            long mois = ChronoUnit.MONTHS.between(depart, r.dateAgeTauxPlein);
            if (depart.plusMonths(mois).isBefore(r.dateAgeTauxPlein)) mois++;
            int manqAge = (int) ((mois + 2) / 3);
            r.trimestresDecote = Math.min(p.constanteEntiere("decote.maxTrimestres"), Math.min(manqDuree, manqAge));
            r.motifTauxPlein = "pas de taux plein : " + manqDuree + " trimestre(s) manquant(s) pour la durée, " + manqAge
                    + " pour atteindre " + r.ageTauxPlein + " → on retient le plus favorable, " + r.trimestresDecote + " (maximum 20)";
        }
        r.decotePct = p.constante("decote.parTrimestre").multiply(BigDecimal.valueOf(r.trimestresDecote)).multiply(CENT).setScale(3, RoundingMode.HALF_UP);
        BigDecimal taux = tauxPleinVal.subtract(p.constante("decote.parTrimestre").multiply(BigDecimal.valueOf(r.trimestresDecote)));
        r.tauxPct = taux.multiply(CENT).setScale(3, RoundingMode.HALF_UP);

        if (r.tauxPlein && depart.isAfter(r.dateAgeLegal) && r.trimestresTousRegimes > r.dureeRequise) {
            // Trimestres cotisés après l'âge légal : années suivantes en entier, année de l'âge légal
            // limitée aux trimestres civils complets restant après la date anniversaire.
            int legalYear = r.dateAgeLegal.getYear();
            int cotisesApres = 0;
            for (AnneeDetail a : r.annees) {
                if (a.annee > legalYear) cotisesApres += a.trimestresCotises;
                else if (a.annee == legalYear) cotisesApres += Math.min(a.trimestresCotises, (12 - r.dateAgeLegal.getMonthValue()) / 3);
            }
            long moisApresLegal = ChronoUnit.MONTHS.between(r.dateAgeLegal, depart);
            int excedent = r.trimestresTousRegimes - r.dureeRequise;
            r.trimestresSurcote = (int) Math.max(0, Math.min(Math.min(moisApresLegal / 3, excedent), cotisesApres));
        }
        BigDecimal surcote = p.constante("surcote.parTrimestre").multiply(BigDecimal.valueOf(r.trimestresSurcote));
        r.surcotePct = surcote.multiply(CENT).setScale(2, RoundingMode.HALF_UP);
        r.etapes.add("Taux : " + (r.tauxPlein ? "taux plein de 50 % (" + r.motifTauxPlein + ")"
                : pct(r.tauxPct) + " % (" + r.motifTauxPlein + ", décote de " + pct(r.decotePct) + " point(s))")
                + (r.trimestresSurcote > 0 ? " ; surcote de " + r.trimestresSurcote + " trimestre(s) × 1,25 % = " + pct(r.surcotePct) + " %" : "") + ".");

        // ---------- 7. Pension de base ----------
        int trimProrata = r.trimestresRegimeGeneral + r.trimestresMajorationEnfants;
        r.prorata = BigDecimal.valueOf(trimProrata).divide(BigDecimal.valueOf(r.dureeRequise), 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        r.prorataPct = r.prorata.multiply(CENT).setScale(2, RoundingMode.HALF_UP);
        r.baseAvantPlafond = r.sam.multiply(taux, MC).multiply(r.prorata, MC).setScale(2, RoundingMode.HALF_UP);
        Montant passDepart = p.pass(anneeDepart);
        r.plafondPensionBase = passDepart.valeur().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal base = r.baseAvantPlafond;
        if (base.compareTo(r.plafondPensionBase) > 0) { base = r.plafondPensionBase; r.basePlafonnee = true; }
        r.baseApresSurcote = base.multiply(BigDecimal.ONE.add(surcote)).setScale(2, RoundingMode.HALF_UP);
        r.majorationEnfantsBase = d.nombreEnfants >= 3
                ? r.baseApresSurcote.multiply(p.constante("majoration.enfants.taux")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        r.etapes.add("Pension de base = SAM × taux × proratisation = " + argent(r.sam) + " × " + pct(r.tauxPct) + " % × "
                + trimProrata + "/" + r.dureeRequise + (trimProrata > r.dureeRequise ? " (plafonné à 1)" : "")
                + " = " + argent(r.baseAvantPlafond) + " € par an"
                + (r.basePlafonnee ? ", ramenée au maximum légal de 50 % du PASS soit " + argent(r.plafondPensionBase) + " €" : "")
                + (r.trimestresSurcote > 0 ? ", puis surcote : " + argent(r.baseApresSurcote) + " €" : "") + ".");
        if (r.majorationEnfantsBase.signum() > 0)
            r.etapes.add("Majoration de 10 % pour 3 enfants ou plus : + " + argent(r.majorationEnfantsBase) + " € par an.");
        else if (d.nombreEnfants > 0)
            r.etapes.add("Majoration pour enfants : non applicable (" + d.nombreEnfants + " enfant(s), il en faut au moins 3).");

        // ---------- 8. Agirc-Arrco ----------
        r.pointsDeclares = pointsDeclares.setScale(2, RoundingMode.HALF_UP);
        r.pointsCalcules = pointsCalcules.setScale(2, RoundingMode.HALF_UP);
        r.pointsTotal = pointsDeclares.add(pointsCalcules).setScale(2, RoundingMode.HALF_UP);
        r.valeurPoint = p.valeurServicePoint(depart);
        r.dateValeurPoint = p.dateValeurServicePoint(depart);
        r.coefficientAnticipation = r.tauxPlein ? BigDecimal.ONE : coefficientAnticipation(r.trimestresDecote);
        BigDecimal compl = r.pointsTotal.multiply(r.valeurPoint, MC).multiply(r.coefficientAnticipation, MC);
        r.majorationEnfantsAgirc = d.nombreEnfants >= 3
                ? compl.multiply(p.constante("majoration.enfants.taux")).min(p.constante("agirc.majorationEnfants.plafond")).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        r.complementaireAnnuelle = compl.add(r.majorationEnfantsAgirc).setScale(2, RoundingMode.HALF_UP);
        r.complementaireMensuelle = mensuel(r.complementaireAnnuelle);
        r.etapes.add("Agirc-Arrco = " + argent(r.pointsTotal) + " points × " + r.valeurPoint.toPlainString().replace('.', ',')
                + " € (valeur au " + date(r.dateValeurPoint) + ")"
                + (r.tauxPlein ? "" : " × coefficient d'anticipation " + r.coefficientAnticipation.toPlainString().replace('.', ','))
                + (r.majorationEnfantsAgirc.signum() > 0 ? " + majoration enfants " + argent(r.majorationEnfantsAgirc) + " €" : "")
                + " = " + argent(r.complementaireAnnuelle) + " € par an. Pas de coefficient de solidarité (supprimé au 1er décembre 2023).");

        // ---------- 9. Minimum contributif ----------
        r.complementMico = BigDecimal.ZERO;
        BigDecimal baseHorsMico = r.baseApresSurcote.add(r.majorationEnfantsBase);
        if (r.tauxPlein) {
            BigDecimal duree = BigDecimal.valueOf(r.dureeRequise);
            BigDecimal micoBase = p.constante("mico.base.mensuel").multiply(DOUZE);
            BigDecimal micoMaj = p.constante("mico.majore.mensuel").multiply(DOUZE);
            BigDecimal cible = micoBase.multiply(BigDecimal.valueOf(trimProrata).divide(duree, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE));
            if (r.trimestresCotises >= p.constanteEntiere("mico.trimestresCotisesMajoration"))
                cible = cible.add(micoMaj.subtract(micoBase).multiply(BigDecimal.valueOf(r.trimestresCotises).divide(duree, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE)));
            if (r.baseApresSurcote.compareTo(cible) < 0) {
                BigDecimal complement = cible.subtract(r.baseApresSurcote);
                BigDecimal plafondTotal = p.constante("mico.plafondPensions.mensuel");
                BigDecimal marge = plafondTotal.multiply(DOUZE).subtract(baseHorsMico).subtract(r.complementaireAnnuelle);
                r.complementMico = complement.min(marge.max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);
                r.micoDetail = "Minimum contributif : la pension de base (" + argent(r.baseApresSurcote) + " €) est inférieure au minimum de "
                        + argent(cible.setScale(2, RoundingMode.HALF_UP)) + " € ; complément de " + argent(r.complementMico)
                        + " € (total des retraites limité à " + argent(plafondTotal) + " € par mois).";
                r.etapes.add(r.micoDetail);
            } else {
                r.micoDetail = "Minimum contributif non applicable : la pension de base dépasse le minimum.";
            }
        } else {
            r.micoDetail = "Minimum contributif non applicable : il exige le taux plein.";
        }
        r.baseAnnuelle = baseHorsMico.add(r.complementMico).setScale(2, RoundingMode.HALF_UP);
        r.baseMensuelle = mensuel(r.baseAnnuelle);

        // ---------- 10. Totaux, net, taux de remplacement ----------
        r.totalBrutAnnuel = r.baseAnnuelle.add(r.complementaireAnnuelle);
        r.totalBrutMensuel = r.baseMensuelle.add(r.complementaireMensuelle);
        BigDecimal tBase = tauxPrelevements(d.tauxCsg, false), tCompl = tauxPrelevements(d.tauxCsg, true);
        r.tauxPrelevementsBasePct = tBase.multiply(CENT).setScale(1, RoundingMode.HALF_UP);
        r.tauxPrelevementsComplPct = tCompl.multiply(CENT).setScale(1, RoundingMode.HALF_UP);
        r.totalNetMensuel = r.baseMensuelle.multiply(BigDecimal.ONE.subtract(tBase))
                .add(r.complementaireMensuelle.multiply(BigDecimal.ONE.subtract(tCompl))).setScale(2, RoundingMode.HALF_UP);
        r.etapes.add("Net : prélèvements sociaux de " + pct(r.tauxPrelevementsBasePct) + " % sur la base et "
                + pct(r.tauxPrelevementsComplPct) + " % sur la complémentaire (" + d.tauxCsg.getLibelle()
                + ") → environ " + argent(r.totalNetMensuel) + " € net par mois, avant impôt sur le revenu.");
        if (dernierSalaire != null && dernierSalaire.signum() > 0) {
            r.dernierSalaireAnnuel = dernierSalaire.setScale(2, RoundingMode.HALF_UP);
            r.tauxRemplacementPct = r.totalBrutAnnuel.divide(dernierSalaire, 4, RoundingMode.HALF_UP).multiply(CENT).setScale(1, RoundingMode.HALF_UP);
        }

        // ---------- 11. Montant revalorisé à la date de calcul ----------
        if (d.dateCalcul != null && depart.isBefore(d.dateCalcul)) {
            r.departPasse = true;
            BigDecimal coefBase = p.revalorisationPensionEntre(depart, d.dateCalcul);
            r.valeurPointActuelle = p.valeurServicePoint(d.dateCalcul);
            r.baseMensuelleActuelle = mensuel(r.baseAnnuelle.multiply(coefBase));
            r.complementaireMensuelleActuelle = mensuel(r.complementaireAnnuelle.multiply(r.valeurPointActuelle).divide(r.valeurPoint, MC));
            r.totalBrutMensuelActuel = r.baseMensuelleActuelle.add(r.complementaireMensuelleActuelle);
        }

        // ---------- 12. Réversion ----------
        calculerReversion(d, r);

        // ---------- Limites ----------
        r.limites.add("Un seul régime de base est simulé en détail (régime général) ; les trimestres d'autres régimes ne comptent que pour le taux.");
        r.limites.add("Surcote approchée : trimestres cotisés après l'âge légal (année par année), dans la limite des trimestres au-delà de la durée requise.");
        r.limites.add("Coefficient d'anticipation Agirc-Arrco calculé selon les trimestres manquants (barème « carrière courte ») ; le barème par âge n'est pas comparé.");
        r.limites.add("Minimum contributif, réversion et prélèvements : barèmes 2026 appliqués quelle que soit l'année de départ.");
        if (regle.isBornesAVerifier())
            r.limites.add("Âge de départ carrière longue pendant la suspension de la réforme : valeur à confirmer avec le simulateur officiel (info-retraite.fr).");
        return r;
    }

    // =====================================================================================
    private void ajouterProjection(DonneesSimulation d, List<LigneCarriere> lignes, ResultatSimulation r) {
        if (d.salaireProjete == null || d.salaireProjete.signum() <= 0) return;
        int derniere = lignes.stream().mapToInt(LigneCarriere::annee).max().orElse(LocalDate.now().getYear() - 1);
        int anneeDepart = d.dateDepart.getYear();
        int ajoutees = 0;
        for (int y = derniere + 1; y <= anneeDepart; y++) {
            BigDecimal rev = d.salaireProjete;
            if (y == anneeDepart) {
                int mois = d.dateDepart.getMonthValue() - 1;
                if (mois == 0) break;
                rev = rev.multiply(BigDecimal.valueOf(mois)).divide(DOUZE, 2, RoundingMode.HALF_UP);
            }
            lignes.add(new LigneCarriere(y, TypePeriode.SALAIRE, rev, Devise.EUR, null, null, true));
            ajoutees++;
        }
        if (ajoutees > 0)
            r.alertes.add("Projection : " + ajoutees + " année(s) ajoutée(s) jusqu'au départ avec un salaire de "
                    + argent(d.salaireProjete) + " € par an (paramètres de la dernière année connue).");
    }

    private BigDecimal calculerPoints(int annee, BigDecimal revenu, Devise devise, boolean projetee) {
        if (devise != Devise.EUR) return null;
        BigDecimal va = p.valeurAchatPoint(annee);
        if (va == null && (projetee || annee > 2026)) va = p.derniereValeurAchatPoint();
        if (va == null) return null;
        BigDecimal pass = convertir(p.pass(annee).valeur(), p.pass(annee).devise(), Devise.EUR);
        BigDecimal t1 = revenu.min(pass);
        BigDecimal t2 = revenu.subtract(pass).max(BigDecimal.ZERO).min(pass.multiply(BigDecimal.valueOf(7)));
        BigDecimal cotis = t1.multiply(p.constante("agirc.tauxT1")).add(t2.multiply(p.constante("agirc.tauxT2")));
        return cotis.divide(va, 2, RoundingMode.HALF_UP);
    }

    private void evaluerCarriereLongue(DonneesSimulation d, RegleGeneration regle,
                                       TreeMap<Integer, Integer> reputesParAnnee, ResultatSimulation r) {
        LocalDate naissance = d.dateNaissance;
        int minimum = naissance.getMonthValue() >= 10 ? 4 : 5;
        BorneCarriereLongue meilleureDebut = null;
        BorneCarriereLongue eligible = null;
        for (BorneCarriereLongue b : regle.getBornes()) {
            int finAnnee = naissance.getYear() + b.ageDebutAvant();
            int cumul = 0;
            for (Map.Entry<Integer, Integer> e : reputesParAnnee.headMap(finAnnee, true).entrySet()) cumul += e.getValue();
            boolean debutOk = cumul >= minimum;
            r.carriereLongueVerifications.add("Début avant " + b.ageDebutAvant() + " ans : " + cumul + " trimestre(s) cotisé(s) à fin "
                    + finAnnee + " (minimum " + minimum + ") " + (debutOk ? "✔" : "✘")
                    + (debutOk ? " → départ possible dès " + b.ageDepart().libelle() + " avec " + b.trimestresCotisesRequis()
                    + " trimestres cotisés (vous en avez " + r.trimestresReputesCotises + ") "
                    + (r.trimestresReputesCotises >= b.trimestresCotisesRequis() ? "✔" : "✘") : ""));
            if (!debutOk) continue;
            if (meilleureDebut == null || avant(b.ageDepart(), meilleureDebut.ageDepart())) meilleureDebut = b;
            boolean dureeOk = r.trimestresReputesCotises >= b.trimestresCotisesRequis();
            boolean ageOk = !d.dateDepart.isBefore(premierDuMoisSuivant(b.ageDepart().atteintLe(naissance)));
            if (dureeOk && ageOk && (eligible == null || avant(b.ageDepart(), eligible.ageDepart()))) eligible = b;
        }
        if (meilleureDebut != null) r.dateCarriereLongue = premierDuMoisSuivant(meilleureDebut.ageDepart().atteintLe(naissance));
        if (eligible != null) {
            r.carriereLongueEligible = true;
            r.carriereLongueDetail = "début avant " + eligible.ageDebutAvant() + " ans, départ possible dès "
                    + eligible.ageDepart().libelle() + ", " + r.trimestresReputesCotises + " trimestres cotisés ou réputés cotisés ≥ "
                    + eligible.trimestresCotisesRequis();
        } else {
            r.carriereLongueEligible = false;
            r.carriereLongueDetail = meilleureDebut == null ? "condition de début de carrière non remplie"
                    : "début de carrière précoce mais durée cotisée insuffisante (" + r.trimestresReputesCotises + " / "
                    + meilleureDebut.trimestresCotisesRequis() + ") ou âge non atteint à la date de départ";
        }
    }

    private void calculerReversion(DonneesSimulation d, ResultatSimulation r) {
        if (!d.conjointDecede) return;
        r.reversionCalculee = true;
        int age = Age.entre(d.dateNaissance, d.dateDepart).annees();
        int ageMin = p.constanteEntiere("reversion.ageMinimum");
        BigDecimal valeur = r.valeurPoint;
        r.reversionBaseAnnuelle = BigDecimal.ZERO;
        r.reversionAgircAnnuelle = BigDecimal.ZERO;
        if (age < ageMin) {
            r.reversionDetail.add("Réversion : il faut avoir au moins " + ageMin + " ans.");
        } else {
            // Régime général : 54 % sous condition de ressources
            if (d.pensionBaseConjoint != null && d.pensionBaseConjoint.signum() > 0) {
                BigDecimal theorique = d.pensionBaseConjoint.multiply(p.constante("reversion.rg.taux"))
                        .max(p.constante("reversion.rg.min.annuel")).min(p.constante("reversion.rg.max.annuel"));
                r.plafondRessourcesReversion = p.smicHoraire(d.dateDepart.getYear()).multiply(BigDecimal.valueOf(2080)).setScale(2, RoundingMode.HALF_UP);
                r.ressourcesRetenues = r.baseAnnuelle.add(r.complementaireAnnuelle)
                        .add(d.autresRessources == null ? BigDecimal.ZERO : d.autresRessources).setScale(2, RoundingMode.HALF_UP);
                BigDecimal marge = r.plafondRessourcesReversion.subtract(r.ressourcesRetenues);
                r.reversionBaseAnnuelle = theorique.min(marge.max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);
                r.reversionDetail.add("Réversion régime général : 54 % de " + argent(d.pensionBaseConjoint) + " € = "
                        + argent(theorique.setScale(2, RoundingMode.HALF_UP)) + " € par an en théorie (bornée entre le minimum et le maximum légaux).");
                r.reversionDetail.add("Ressources retenues : " + argent(r.ressourcesRetenues) + " € pour un plafond de "
                        + argent(r.plafondRessourcesReversion) + " € (2 080 × SMIC horaire) → réversion versée : "
                        + argent(r.reversionBaseAnnuelle) + " € par an" + (r.reversionBaseAnnuelle.compareTo(theorique) < 0 ? " (réduite par le plafond de ressources)." : "."));
            } else {
                r.reversionDetail.add("Réversion régime général : renseignez la retraite de base du conjoint décédé pour la calculer.");
            }
            // Agirc-Arrco : 60 % des points, sans condition de ressources, perdue en cas de remariage
            if (d.remarie) {
                r.reversionDetail.add("Réversion Agirc-Arrco : supprimée en cas de remariage.");
            } else if (d.pointsConjoint != null && d.pointsConjoint.signum() > 0) {
                r.reversionAgircAnnuelle = d.pointsConjoint.multiply(p.constante("reversion.agirc.taux")).multiply(valeur).setScale(2, RoundingMode.HALF_UP);
                r.reversionDetail.add("Réversion Agirc-Arrco : 60 % × " + argent(d.pointsConjoint) + " points × "
                        + valeur.toPlainString().replace('.', ',') + " € = " + argent(r.reversionAgircAnnuelle) + " € par an (sans condition de ressources).");
            } else {
                r.reversionDetail.add("Réversion Agirc-Arrco : renseignez les points du conjoint décédé pour la calculer.");
            }
        }
        r.reversionMensuelle = mensuel(r.reversionBaseAnnuelle.add(r.reversionAgircAnnuelle));
        r.reversionDetail.add("La réversion peut être demandée dès 55 ans, indépendamment de sa propre retraite ; le plafond de ressources s'apprécie à la date de la demande.");
    }

    // =====================================================================================
    private List<Scenario> comparerDates(DonneesSimulation d, ResultatSimulation ref) {
        TreeMap<LocalDate, String> dates = new TreeMap<>();
        if (ref.dateCarriereLongue != null && ref.dateCarriereLongue.isBefore(ref.dateAgeLegal))
            dates.put(ref.dateCarriereLongue, "Âge carrière longue");
        dates.put(premierDuMoisSuivant(ref.dateAgeLegal), "Âge légal");
        dates.put(premierDuMoisSuivant(ref.dateAgeLegal.plusYears(1)), "Âge légal + 1 an");
        dates.put(premierDuMoisSuivant(ref.dateAgeTauxPlein), "Taux plein automatique");
        dates.merge(d.dateDepart, "Date choisie", (a, b) -> b + " (" + a + ")");
        List<Scenario> res = new ArrayList<>();
        for (Map.Entry<LocalDate, String> e : dates.entrySet()) {
            ResultatSimulation r = e.getKey().equals(d.dateDepart) ? ref : calculer(d.copieAvecDepart(e.getKey()));
            Scenario s = new Scenario();
            s.libelle = e.getValue();
            s.date = e.getKey();
            s.age = r.ageAuDepart;
            s.possible = r.departPossible;
            s.motif = r.departPossible ? (r.tauxPlein ? "Taux plein" : "Décote " + r.trimestresDecote + " trim.")
                    + (r.trimestresSurcote > 0 ? ", surcote " + r.trimestresSurcote + " trim." : "")
                    : "Départ impossible : " + r.carriereLongueDetail;
            s.trimestres = r.trimestresTousRegimes;
            s.tauxPct = r.tauxPct;
            s.baseMensuel = r.baseMensuelle;
            s.complementaireMensuel = r.complementaireMensuelle;
            s.totalMensuel = r.totalBrutMensuel;
            s.netMensuel = r.totalNetMensuel;
            s.choisi = e.getKey().equals(d.dateDepart);
            res.add(s);
        }
        if (d.salaireProjete == null || d.salaireProjete.signum() <= 0)
            ref.limites.add("Comparaison des dates : sans salaire projeté, les trimestres et points postérieurs à la dernière année saisie ne sont pas ajoutés.");
        return res;
    }

    // =====================================================================================
    static BigDecimal coefficientAnticipation(int trimestresManquants) {
        int q = Math.max(0, Math.min(20, trimestresManquants));
        if (q == 0) return BigDecimal.ONE;
        BigDecimal c;
        if (q <= 10) c = BigDecimal.ONE.subtract(new BigDecimal("0.01").multiply(BigDecimal.valueOf(q)));
        else if (q == 11) c = new BigDecimal("0.89");
        else c = new BigDecimal("0.88").subtract(new BigDecimal("0.0125").multiply(BigDecimal.valueOf(q - 12)));
        return c.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private BigDecimal tauxPrelevements(DonneesSimulation.TauxCsg t, boolean complementaire) {
        BigDecimal csg, crds = p.constante("prelevement.crds"), casa = p.constante("prelevement.casa");
        switch (t) {
            case NORMAL -> csg = p.constante("prelevement.csg.normal");
            case MEDIAN -> csg = p.constante("prelevement.csg.median");
            case REDUIT -> { csg = p.constante("prelevement.csg.reduit"); casa = BigDecimal.ZERO; }
            default -> { return BigDecimal.ZERO; }
        }
        BigDecimal total = csg.add(crds).add(casa);
        if (complementaire) total = total.add(p.constante("prelevement.maladieComplementaire"));
        return total;
    }

    private BigDecimal convertir(BigDecimal v, Devise de, Devise vers) {
        if (de == vers) return v;
        BigDecimal taux = p.constante("conversion.frf");
        return de == Devise.FRF ? v.divide(taux, 6, RoundingMode.HALF_UP) : v.multiply(taux);
    }

    static LocalDate premierDuMoisSuivant(LocalDate d) {
        return d.getDayOfMonth() == 1 ? d : d.withDayOfMonth(1).plusMonths(1);
    }

    private static boolean avant(Age a, Age b) { return a.annees() * 12 + a.mois() < b.annees() * 12 + b.mois(); }

    private static BigDecimal mensuel(BigDecimal annuel) { return annuel.divide(DOUZE, 2, RoundingMode.HALF_UP); }

    private static final DecimalFormatSymbols FR = DecimalFormatSymbols.getInstance(Locale.FRANCE);

    static String argent(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", FR);
        return f.format(v).replace(' ', ' ').replace(' ', ' ');
    }

    static String pct(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("0.###", FR);
        return f.format(v);
    }

    static String date(LocalDate d) {
        return String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
