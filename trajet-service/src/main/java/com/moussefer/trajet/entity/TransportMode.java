package com.moussefer.trajet.entity;

/**
 * Modes de transport supportés par la plateforme Moussefer.
 *
 * <h3>État actuel (PFE — V24)</h3>
 * Seul le mode {@link #LOUAGE} est actif et entièrement implémenté.
 * Le louage est le cas d'usage principal de Moussefer et couvre 100% des
 * scénarios métier livrés (réservation, paiement, chat, fidélité, etc.).
 *
 * <h3>Évolutions futures (post-PFE)</h3>
 * L'enum est conçu pour être étendu sans refactor :
 * <ul>
 *   <li><b>TAXI</b> — 4 places, tarification au compteur ou forfait,
 *       pas d'algorithme de file d'attente, réservation à la course.</li>
 *   <li><b>BUS</b> — capacité 30-50 places, ligne fixe, horaires fixes,
 *       réservation par siège ou ticket, pas de priorité chauffeur.</li>
 *   <li><b>METRO</b> — capacité par rame (illimité côté réservation),
 *       ticket unitaire à validation au passage, pas de réservation
 *       par siège.</li>
 * </ul>
 *
 * <p>L'ajout d'un nouveau mode suit le pattern Strategy :
 * <pre>
 * 1. Ajouter la valeur dans cet enum
 * 2. Implémenter une classe TransportStrategy correspondante
 *    (ex: TaxiStrategy, BusStrategy)
 * 3. Le TransportStrategyFactory récupère automatiquement la bonne
 *    stratégie via le mode du Trajet — aucune modification de
 *    TrajetService nécessaire.
 * </pre>
 *
 * Les services transverses (auth, paiement, chat, notifications, fidélité)
 * sont déjà agnostiques au mode et n'auraient pas à être modifiés.
 */
public enum TransportMode {
    /**
     * Louage interurbain tunisien — capacité 8 places, tarif régulé par
     * le Ministère du Transport, algorithme de priorité FIFO sur file
     * d'attente (1er chauffeur ACTIVE, suivants LOCKED).
     *
     * Voir {@link com.moussefer.trajet.service.TrajetService#LOUAGE_SEATS}.
     */
    LOUAGE
    // Roadmap V25+ :
    // TAXI,    // 4 places, tarif compteur ou forfait
    // BUS,     // 30-50 places, ligne et horaires fixes
    // METRO    // ticket unitaire, pas de réservation par siège
}
