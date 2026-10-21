package com.baseballstats.baseballstats.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signs up, signs in and edits favorites against an in-memory database. */
@SpringBootTest
@AutoConfigureMockMvc
class AccountFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private JsonNode postJson(String url, String body, int expectedStatus) throws Exception {
        String response = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    @Test
    void signUpSignInAndSaveFavorites() throws Exception {
        JsonNode created = postJson("/api/auth/register", "{\"username\":\"Brew_Fan\",\"password\":\"correct horse\"}", 200);
        String token = created.get("token").asText();
        assertEquals("brew_fan", created.at("/user/username").asText());
        String bearer = "Bearer " + token;

        // Add, list, replace and remove favorites
        mvc.perform(put("/api/favorites/158").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.favorites[0]").value(158));
        mvc.perform(put("/api/favorites/158").header("Authorization", bearer)) // adding twice is harmless
                .andExpect(jsonPath("$.favorites.length()").value(1));
        mvc.perform(put("/api/favorites").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"teamIds\":[112,158,112]}"))
                .andExpect(jsonPath("$.favorites[0]").value(112))
                .andExpect(jsonPath("$.favorites[1]").value(158))
                .andExpect(jsonPath("$.favorites.length()").value(2));
        mvc.perform(delete("/api/favorites/112").header("Authorization", bearer))
                .andExpect(jsonPath("$.favorites.length()").value(1));

        // Usernames aren't case-sensitive, and a new sign-in sees the saved favorites
        JsonNode again = postJson("/api/auth/login", "{\"username\":\"BREW_FAN\",\"password\":\"correct horse\"}", 200);
        assertEquals(158, again.at("/favorites/0").asInt());
        assertNotEquals(token, again.get("token").asText());

        // Signing out ends that session only
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + again.get("token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value("brew_fan"));
    }

    @Test
    void rejectsBadSignUpsAndSignIns() throws Exception {
        postJson("/api/auth/register", "{\"username\":\"taken_name\",\"password\":\"password123\"}", 200);
        postJson("/api/auth/register", "{\"username\":\"Taken_Name\",\"password\":\"password123\"}", 409);
        postJson("/api/auth/register", "{\"username\":\"ab\",\"password\":\"password123\"}", 400);
        postJson("/api/auth/register", "{\"username\":\"has space\",\"password\":\"password123\"}", 400);
        postJson("/api/auth/register", "{\"username\":\"shortpw\",\"password\":\"short\"}", 400);

        JsonNode wrong = postJson("/api/auth/login", "{\"username\":\"taken_name\",\"password\":\"wrong-password\"}", 401);
        assertEquals("Wrong username or password.", wrong.get("error").asText());
        postJson("/api/auth/login", "{\"username\":\"nobody_here\",\"password\":\"password123\"}", 401);
    }

    @Test
    void pingAnswersWithoutSignIn() throws Exception {
        mvc.perform(get("/api/ping")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void favoritesNeedASignIn() throws Exception {
        mvc.perform(get("/api/favorites")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/favorites").header("Authorization", "Bearer made-up-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleSignInReportsWhenNotSetUp() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.passwordSignIn").value(true));
        postJson("/api/auth/google", "{\"credential\":\"x\"}", 503);
    }
}
