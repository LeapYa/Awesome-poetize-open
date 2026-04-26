package com.ld.poetry.service.prerender;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
class PrerenderPlanner {

    private final PrerenderGraphResolver graphResolver;

    PrerenderPlan plan(PrerenderRequest request, PrerenderSnapshot snapshot) {
        LinkedHashMap<String, PrerenderNode> visited = new LinkedHashMap<>();
        LinkedHashSet<String> visiting = new LinkedHashSet<>();
        ArrayList<PrerenderNode> discovered = new ArrayList<>();

        for (PrerenderNode seed : request.seeds()) {
            dfs(seed, snapshot, visiting, visited, discovered);
        }

        List<PrerenderNode> renderNodes = discovered.stream()
                .filter(PrerenderNode::renderable)
                .sorted(Comparator.comparingInt(PrerenderNode::priority).thenComparing(PrerenderNode::key))
                .toList();
        return new PrerenderPlan(request.description(), renderNodes);
    }

    private void dfs(PrerenderNode node, PrerenderSnapshot snapshot, Set<String> visiting,
                     Map<String, PrerenderNode> visited, List<PrerenderNode> discovered) {
        if (node == null || visited.containsKey(node.key())) {
            return;
        }
        if (!visiting.add(node.key())) {
            throw new IllegalStateException("检测到预渲染图循环: " + node.key());
        }

        for (PrerenderNode child : graphResolver.childrenOf(node, snapshot)) {
            dfs(child, snapshot, visiting, visited, discovered);
        }

        visiting.remove(node.key());
        visited.put(node.key(), node);
        discovered.add(node);
    }
}
