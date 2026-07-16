package com.ld.poetry.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceReferenceServiceTest {

    private static final String SOURCE_PATH = "/static/articlePicture/a.png";
    private static final String TARGET_URL = "https://cdn.example.com/migrated/a.png";

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ResourceReferenceService service;

    @BeforeEach
    void setUp() {
        service = new ResourceReferenceService(jdbcTemplate);
    }

    @Test
    void replaceReferencesShouldUseExactIdentityAndPreserveFragment() {
        String sourceWithQuery = SOURCE_PATH + "?size=large";
        String original = "{"
                + "\"exact\":\"/static/articlePicture/a.png?size=large#hero\","
                + "\"differentQuery\":\"/static/articlePicture/a.png?size=small\","
                + "\"differentHost\":\"https://old.example.com/static/articlePicture/a.png?size=large\","
                + "\"prefix\":\"/static/articlePicture/a.png.backup\","
                + "\"nested\":\"/static/articlePicture/a.png/child\""
                + "}";
        stubSingleArticleContentRow(sourceWithQuery, original, 1);

        ResourceReferenceService.ReplacementResult result =
                service.replaceReferences(sourceWithQuery, TARGET_URL);

        assertThat(result.updatedRows()).isEqualTo(1);
        assertThat(result.articleIds()).containsExactly(42);
        assertThat(result.changedDomains()).containsExactly("ARTICLE");

        ArgumentCaptor<Object> replacedValue = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(anyString(), replacedValue.capture(), any(), any());
        assertThat(replacedValue.getValue()).isEqualTo("{"
                + "\"exact\":\"https://cdn.example.com/migrated/a.png#hero\","
                + "\"differentQuery\":\"/static/articlePicture/a.png?size=small\","
                + "\"differentHost\":\"https://old.example.com/static/articlePicture/a.png?size=large\","
                + "\"prefix\":\"/static/articlePicture/a.png.backup\","
                + "\"nested\":\"/static/articlePicture/a.png/child\""
                + "}");
    }

    @Test
    void replaceReferencesShouldNotReplaceSamePathOnDifferentHost() {
        String source = "https://trusted.example.com/static/articlePicture/a.png";
        String original = "https://trusted.example.com/static/articlePicture/a.png "
                + "https://other.example.com/static/articlePicture/a.png";
        stubSingleArticleContentRow(source, original, 1);

        service.replaceReferences(source, TARGET_URL);

        ArgumentCaptor<Object> replacedValue = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(anyString(), replacedValue.capture(), any(), any());
        assertThat(replacedValue.getValue()).isEqualTo(
                TARGET_URL + " https://other.example.com/static/articlePicture/a.png"
        );
    }

    @Test
    void replaceReferencesShouldAbortWhenReferencedRowChangesConcurrently() {
        stubSingleArticleContentRow(SOURCE_PATH, "![cover](" + SOURCE_PATH + ")", 0);

        assertThatThrownBy(() -> service.replaceReferences(SOURCE_PATH, TARGET_URL))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining("article.article_content");
    }

    private void stubSingleArticleContentRow(String sourceUrl, String originalValue, int updatedRows) {
        when(jdbcTemplate.queryForList(anyString(), eq(sourceUrl))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("FROM `article`") && sql.contains("`article_content`")) {
                return List.of(Map.of(
                        "row_id", 7,
                        "ref_value", originalValue,
                        "cache_id", 42
                ));
            }
            return List.of();
        });
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(updatedRows);
    }
}