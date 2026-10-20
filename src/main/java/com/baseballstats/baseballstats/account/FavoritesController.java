package com.baseballstats.baseballstats.account;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The signed-in person's favorite teams (MLB team ids).
 *
 *   GET    /api/favorites           {"favorites": [158, 112]}
 *   PUT    /api/favorites           {"teamIds": [158, 112]}  replace the whole list
 *   PUT    /api/favorites/{teamId}  add one
 *   DELETE /api/favorites/{teamId}  remove one
 */
@RestController
public class FavoritesController {

    public record FavoritesForm(List<Integer> teamIds) {
    }

    private final AccountStore store;

    public FavoritesController(AccountStore store) {
        this.store = store;
    }

    @GetMapping("/api/favorites")
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        long userId = AuthController.requireAccount(store, authorization).id();
        return Map.of("favorites", store.favorites(userId));
    }

    @PutMapping("/api/favorites")
    public Map<String, Object> replace(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody FavoritesForm form) {
        long userId = AuthController.requireAccount(store, authorization).id();
        List<Integer> ids = form.teamIds() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(form.teamIds()));
        ids.removeIf(id -> id == null);
        if (ids.size() > AccountStore.MAX_FAVORITES) {
            throw ApiException.badRequest("You can have up to " + AccountStore.MAX_FAVORITES + " favorite teams.");
        }
        ids.forEach(AccountRules::checkTeamId);
        store.replaceFavorites(userId, ids);
        return Map.of("favorites", store.favorites(userId));
    }

    @PutMapping("/api/favorites/{teamId}")
    public Map<String, Object> add(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @PathVariable int teamId) {
        long userId = AuthController.requireAccount(store, authorization).id();
        AccountRules.checkTeamId(teamId);
        store.addFavorite(userId, teamId);
        return Map.of("favorites", store.favorites(userId));
    }

    @DeleteMapping("/api/favorites/{teamId}")
    public Map<String, Object> remove(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable int teamId) {
        long userId = AuthController.requireAccount(store, authorization).id();
        store.removeFavorite(userId, teamId);
        return Map.of("favorites", store.favorites(userId));
    }
}
