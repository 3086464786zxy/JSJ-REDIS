package com.itmk.web.sys_menu.entity;

import org.springframework.beans.BeanUtils;

import java.util.*;

/**
 * Builds an adjacency index once. Duplicate IDs, unreachable cycles and null records are ignored.
 */
public final class MakeMenuTree {
    private MakeMenuTree() {}

    private static Map<Long, List<SysMenu>> index(List<SysMenu> source) {
        Map<Long, List<SysMenu>> index = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        if (source != null)
            for (SysMenu menu : source) {
                if (menu != null
                        && menu.getMenuId() != null
                        && menu.getParentId() != null
                        && ids.add(menu.getMenuId()))
                    index.computeIfAbsent(menu.getParentId(), id -> new ArrayList<>()).add(menu);
            }
        return index;
    }

    private record Level<T>(Long id, List<T> output, int depth) {}

    public static List<SysMenu> makeTree(List<SysMenu> source, Long pid) {
        var index = index(source);
        List<SysMenu> roots = new ArrayList<>();
        Deque<Level<SysMenu>> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        seen.add(pid);
        queue.add(new Level<>(pid, roots, 0));
        while (!queue.isEmpty()) {
            var level = queue.removeFirst();
            if (level.depth() >= 32 && !index.getOrDefault(level.id(), List.of()).isEmpty())
                throw new IllegalArgumentException("菜单层级不能超过32层");
            for (SysMenu item : index.getOrDefault(level.id(), List.of()))
                if (seen.add(item.getMenuId())) {
                    SysMenu copy = new SysMenu();
                    BeanUtils.copyProperties(item, copy);
                    copy.setLabel(item.getTitle());
                    copy.setValue(item.getMenuId());
                    copy.setChildren(new ArrayList<>());
                    level.output().add(copy);
                    queue.addLast(
                            new Level<>(item.getMenuId(), copy.getChildren(), level.depth() + 1));
                }
        }
        return roots;
    }

    private static String[] codes(String value) {
        return value == null
                ? new String[0]
                : Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(v -> !v.isEmpty())
                        .distinct()
                        .toArray(String[]::new);
    }

    public static List<RouterVO> makeRouter(List<SysMenu> source, Long pid) {
        var index = index(source);
        List<RouterVO> roots = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        seen.add(pid);
        Deque<Level<RouterVO>> queue = new ArrayDeque<>();
        queue.add(new Level<>(pid, roots, 0));
        while (!queue.isEmpty()) {
            var level = queue.removeFirst();
            if (level.depth() >= 32 && !index.getOrDefault(level.id(), List.of()).isEmpty())
                throw new IllegalArgumentException("菜单层级不能超过32层");
            for (SysMenu item : index.getOrDefault(level.id(), List.of()))
                if (seen.add(item.getMenuId())) {
                    RouterVO router = new RouterVO();
                    router.setName(item.getName());
                    router.setPath(item.getPath());
                    router.setComponent(item.getUrl());
                    router.setMeta(
                            router
                            .new Meta(item.getTitle(), item.getIcon(), codes(item.getCode())));
                    level.output().add(router);
                    if (Long.valueOf(0).equals(item.getParentId())) {
                        router.setComponent("Layout");
                        if ("1".equals(item.getType())) {
                            RouterVO child = new RouterVO();
                            child.setName(item.getName());
                            child.setPath(item.getPath());
                            child.setComponent(item.getUrl());
                            child.setMeta(
                                    child
                                    .new Meta(
                                            item.getTitle(),
                                            item.getIcon(),
                                            codes(item.getCode())));
                            router.setChildren(new ArrayList<>(List.of(child)));
                            router.setRedirect(item.getPath());
                            router.setName(item.getName() + "parent");
                            router.setPath(item.getPath() + "parent");
                            queue.addLast(
                                    new Level<>(
                                            item.getMenuId(),
                                            child.getChildren(),
                                            level.depth() + 1));
                            continue;
                        }
                    }
                    queue.addLast(
                            new Level<>(item.getMenuId(), router.getChildren(), level.depth() + 1));
                }
        }
        return roots;
    }
}
