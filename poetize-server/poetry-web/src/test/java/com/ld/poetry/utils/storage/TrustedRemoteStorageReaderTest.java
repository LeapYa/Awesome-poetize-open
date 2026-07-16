package com.ld.poetry.utils.storage;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedRemoteStorageReaderTest {

    private final TrustedRemoteStorageReader reader = new TrustedRemoteStorageReader();

    @Test
    void parsesExactOriginsFromUrlsAndHostNames() {
        Set<String> origins = reader.parseTrustedHosts(
                "https://cdn.example.com/files, http://images.example.com:8080/api",
                "static.example.com"
        );

        assertThat(origins).containsExactlyInAnyOrder(
                "https://cdn.example.com",
                "http://images.example.com:8080",
                "https://static.example.com"
        );
    }

    @Test
    void rejectsSameHostWhenSchemeDoesNotMatch() {
        assertThatThrownBy(() -> reader.open(
                "http://example.com/image.png",
                Set.of("https://example.com"),
                1024
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("白名单");
    }

    @Test
    void rejectsUntrustedHostBeforeSendingRequest() {
        assertThatThrownBy(() -> reader.open(
                "https://untrusted.example/image.png",
                Set.of("https://trusted.example"),
                1024
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("白名单");
    }

    @Test
    void rejectsPrivateAddressByDefault() {
        assertThatThrownBy(() -> reader.open(
                "http://127.0.0.1:18080/image.png",
                Set.of("http://127.0.0.1:18080"),
                1024
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("受保护网络地址");
    }

    @Test
    void rejectsUserInfoAndFragments() {
        assertThatThrownBy(() -> reader.open(
                "https://user@example.com/image.png",
                Set.of("https://example.com"),
                1024
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> reader.open(
                "https://example.com/image.png#fragment",
                Set.of("https://example.com"),
                1024
        )).isInstanceOf(IllegalArgumentException.class);
    }
}