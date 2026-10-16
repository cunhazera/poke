package com.example.tasks.user;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.example.tasks.support.ApiTest;

class UserApiTest extends ApiTest {

	@Test
	void listsTheFiveDefaultUsers() throws Exception {
		mvc.perform(get("/api/users"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)))
				.andExpect(jsonPath("$[*].username").value(contains("ada", "alan", "grace", "linus", "margaret")));
	}

	@Test
	void getsOneUser() throws Exception {
		mvc.perform(get("/api/users/1"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "id": 1, "fullName": "Ada Lovelace", "username": "ada", "email": "ada@example.com" }
						""", org.springframework.test.json.JsonCompareMode.STRICT));
	}

	@Test
	void unknownAndMalformedIds() throws Exception {
		mvc.perform(get("/api/users/99")).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("User 99 not found"));
		mvc.perform(get("/api/users/abc")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/users/0")).andExpect(status().isBadRequest());
	}
}
