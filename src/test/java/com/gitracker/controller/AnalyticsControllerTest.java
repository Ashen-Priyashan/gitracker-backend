package com.gitracker.controller;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gitracker.entity.GitHubUser;
import com.gitracker.entity.UserAnalytics;
import com.gitracker.service.UserAnalyticsService;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAnalyticsService userAnalyticsService;

    @Test
    void shouldReturnAnalytics() throws Exception {

        GitHubUser user = GitHubUser.builder()
                .username("octocat")
                .build();

        UserAnalytics analytics = UserAnalytics.builder()
                .totalRepositories(8)
                .totalStars(21598)
                .totalForks(164958)
                .mostUsedLanguage("CSS")
                .calculatedAt(LocalDateTime.now())
                .githubUser(user)
                .build();

        when(userAnalyticsService.generateAnalytics("octocat"))
                .thenReturn(analytics);

        mockMvc.perform(get("/api/analytics/octocat")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRepositories").value(8))
                .andExpect(jsonPath("$.totalStars").value(21598))
                .andExpect(jsonPath("$.totalForks").value(164958))
                .andExpect(jsonPath("$.mostUsedLanguage").value("CSS"));
    }
}