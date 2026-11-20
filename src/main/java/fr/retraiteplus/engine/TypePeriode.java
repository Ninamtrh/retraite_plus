package fr.retraiteplus.engine;

/**
 * Nature d'une ligne de carrière.
 * <p>
 * Une période SALAIRE donne des trimestres cotisés. Les autres sont des périodes
 * assimilées : elles comptent dans la durée d'assurance (taux, proratisation) et,
 * pour la retraite anticipée carrière longue, sont « réputées cotisées » dans une
 * limite propre à chaque type (article D351-1-2 CSS, service-public F13845).
 */
public enum TypePeriode {
    SALAIRE("Activité salariée", Integer.MAX_VALUE),
    SERVICE_NATIONAL("Service national", 4),
    CHOMAGE("Chômage indemnisé", 4),
    MALADIE("Maladie / accident du travail", 4),
    MATERNITE("Maternité / adoption", Integer.MAX_VALUE),
    INVALIDITE("Invalidité", 2),
    AUTRE("Autre période assimilée", 0);

    private final String libelle;
    /** Nombre maximum de trimestres de ce type réputés cotisés pour la carrière longue. */
    private final int plafondReputeCotise;

    TypePeriode(String libelle, int plafondReputeCotise) {
        this.libelle = libelle;
        this.plafondReputeCotise = plafondReputeCotise;
    }

    public String getLibelle() { return libelle; }
    public int getPlafondReputeCotise() { return plafondReputeCotise; }

    public static TypePeriode depuisTexte(String s) {
        String t = s == null ? "" : s.trim().toUpperCase()
                .replace('É', 'E').replace('È', 'E').replace(' ', '_').replace('-', '_');
        if (t.isEmpty()) return SALAIRE;
        if (t.startsWith("SAL")) return SALAIRE;
        if (t.startsWith("SERV") || t.equals("SN")) return SERVICE_NATIONAL;
        if (t.startsWith("CHOM")) return CHOMAGE;
        if (t.startsWith("MAL") || t.equals("AT")) return MALADIE;
        if (t.startsWith("MAT") || t.startsWith("ADOP")) return MATERNITE;
        if (t.startsWith("INVAL")) return INVALIDITE;
        if (t.startsWith("AUT")) return AUTRE;
        throw new IllegalArgumentException("type de période inconnu : " + s);
    }
}
