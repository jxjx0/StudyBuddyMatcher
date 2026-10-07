package com.studybuddy.matching.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import com.studybuddy.matching.model.MatchingCriterion;

/**
 * Weights every enabled criterion evenly.
 */
@Component
public class BalancedMatchingStrategy implements MatchingStrategy {

    @Override
    public String getName() {
        return "BALANCED";
    }

    @Override
    public List<MatchingCriterion> applyStrategy(List<MatchingCriterion> criteria) {
        // TODO: distribute weight evenly across all enabled criteria
        int enabledCount = 0;
        // count enabled criteria
        for (int i = 0; i < criteria.size(); i++) {
            if (criteria.get(i).isEnabled()) {
                enabledCount++;
            }
        }
        // distribute weight evenly
        double weight = 1.0 / enabledCount;

        for (int i = 0; i < criteria.size(); i++) {
            if (criteria.get(i).isEnabled()) {
                criteria.get(i).setWeight(weight);
            }
        }
        return criteria;
    }
}
