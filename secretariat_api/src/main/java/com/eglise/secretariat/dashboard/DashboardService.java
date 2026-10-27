package com.eglise.secretariat.dashboard;

import com.eglise.secretariat.dashboard.dto.DashboardStatsDto;
import com.eglise.secretariat.fidele.FideleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service de calcul des statistiques du tableau de bord.
 * Optimisé pour déléguer les agrégations directement à la base de données PostgreSQL.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final FideleRepository fideleRepository;

    public DashboardService(FideleRepository fideleRepository) {
        this.fideleRepository = fideleRepository;
    }

    public DashboardStatsDto getDashboardStats() {
        // 1. Total d'inscrits via COUNT SQL
        long totalInscrits = fideleRepository.countTotalFideles();

        // 2. Répartition par quartier optimisée via GROUP BY SQL
        Map<String, Long> repartitionParQuartier = new HashMap<>();
        List<Object[]> quartiersData = fideleRepository.countFidelesByQuartier();
        for (Object[] row : quartiersData) {
            String domicile = (String) row[0];
            String quartier = (domicile != null && !domicile.trim().isEmpty()) ? domicile.trim() : "Inconnu";
            Long count = (Long) row[1];
            repartitionParQuartier.put(quartier, repartitionParQuartier.getOrDefault(quartier, 0L) + count);
        }

        // 3. Taux de fidèles payant la dîme via GROUP BY SQL
        long dimePayeCount = 0;
        List<Object[]> dimeData = fideleRepository.countFidelesByPayeDime();
        for (Object[] row : dimeData) {
            Boolean paye = (Boolean) row[0];
            if (Boolean.TRUE.equals(paye)) {
                dimePayeCount = (Long) row[1];
            }
        }

        double tauxDime = totalInscrits > 0 ? ((double) dimePayeCount * 100.0) / totalInscrits : 0.0;
        tauxDime = Math.round(tauxDime * 100.0) / 100.0;

        // 4. Flux mensuels agrégés
        Map<String, Long> fluxMensuels = new TreeMap<>();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        fideleRepository.findAllForDashboard().forEach(f -> {
            LocalDate dateInsc = f.getDateInscription();
            if (dateInsc == null && f.getStatutFidele() != null) {
                dateInsc = f.getStatutFidele().getEstMembreActifDepuis();
            }
            if (dateInsc == null) {
                dateInsc = LocalDate.now();
            }
            String moisStr = dateInsc.format(monthFormatter);
            fluxMensuels.put(moisStr, fluxMensuels.getOrDefault(moisStr, 0L) + 1);
        });

        return new DashboardStatsDto(totalInscrits, repartitionParQuartier, tauxDime, fluxMensuels);
    }
}
