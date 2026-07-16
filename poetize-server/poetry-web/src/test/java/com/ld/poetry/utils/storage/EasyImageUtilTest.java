package com.ld.poetry.utils.storage;

import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.vo.FileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EasyImageUtilTest {

    private static final String EASYIMAGE_URL = "https://easy.example.com/api/index.php";
    private static final String EASYIMAGE_TOKEN = "test-token";
    private static final String TRUSTED_HOST = "easy.example.com";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RemoteStorageVerifier remoteStorageVerifier;

    @Mock
    private TrustedRemoteStorageReader trustedRemoteStorageReader;

    private EasyImageUtil util;

    @BeforeEach
    void setUp() {
        util = new EasyImageUtil();
        ReflectionTestUtils.setField(util, "easyImageUrl", EASYIMAGE_URL);
        ReflectionTestUtils.setField(util, "easyImageToken", EASYIMAGE_TOKEN);
        ReflectionTestUtils.setField(util, "easyImageEnable", true);
        ReflectionTestUtils.setField(util, "easyImageDownloadHosts", TRUSTED_HOST);
        ReflectionTestUtils.setField(util, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(util, "remoteStorageVerifier", remoteStorageVerifier);
        ReflectionTestUtils.setField(util, "trustedRemoteStorageReader", trustedRemoteStorageReader);

        lenient().when(trustedRemoteStorageReader.parseTrustedHosts(EASYIMAGE_URL, TRUSTED_HOST))
                .thenReturn(Set.of("https://easy.example.com"));
        lenient().when(trustedRemoteStorageReader.isTrustedPublicUrl(anyString(), anySet()))
                .thenReturn(true);
    }

    @Test
    void saveFileShouldStoreDelUrlAsStorageKeyAndImageUrlAsVisitPathOnSuccess() throws Exception {
        FileVO fileVO = buildRequest();
        Object response = buildEasyImageResponse(
                "success", 200,
                "https:\\/\\/easy.example.com\\/i\\/2024\\/abc.png",
                "https://easy.example.com/del?hash=abc-token"
        );
        when(restTemplate.postForObject(eq(EASYIMAGE_URL), any(), any(Class.class)))
                .thenReturn(response);

        FileVO result = util.saveFile(fileVO);

        assertThat(result.getVisitPath()).isEqualTo("https://easy.example.com/i/2024/abc.png");
        assertThat(result.getStorageKey()).isEqualTo("https://easy.example.com/del?hash=abc-token");
        assertThat(result.getStoreType()).isEqualTo(StoreEnum.EASYIMAGE.getCode());
        assertThat(result.getAbsolutePath()).isEqualTo("https://easy.example.com/i/2024/abc.png");
    }

    @Test
    void saveFileShouldFailWhenDelUrlMissing() throws Exception {
        FileVO fileVO = buildRequest();
        Object response = buildEasyImageResponse(
                "success", 200,
                "https://easy.example.com/i/2024/abc.png",
                null
        );
        when(restTemplate.postForObject(eq(EASYIMAGE_URL), any(), any(Class.class)))
                .thenReturn(response);

        assertThatThrownBy(() -> util.saveFile(fileVO))
                .isInstanceOf(PoetryRuntimeException.class)
                .hasMessageContaining("未返回删除凭证")
                .hasMessageContaining("简单图床上传出错");

        verify(restTemplate).postForObject(eq(EASYIMAGE_URL), any(), any(Class.class));
    }

    @Test
    void saveFileShouldFailWhenResponseResultNotSuccess() throws Exception {
        FileVO fileVO = buildRequest();
        Object response = buildEasyImageResponse(
                "error", 500,
                null,
                null
        );
        when(restTemplate.postForObject(eq(EASYIMAGE_URL), any(), any(Class.class)))
                .thenReturn(response);

        assertThatThrownBy(() -> util.saveFile(fileVO))
                .isInstanceOf(PoetryRuntimeException.class)
                .hasMessageContaining("简单图床上传失败");
    }

    @Test
    void saveFileShouldFailWhenEasyImageDisabled() {
        ReflectionTestUtils.setField(util, "easyImageEnable", false);
        FileVO fileVO = buildRequest();

        assertThatThrownBy(() -> util.saveFile(fileVO))
                .isInstanceOf(PoetryRuntimeException.class)
                .hasMessageContaining("未正确配置");
    }

    @Test
    void saveFileShouldFailWhenUrlOrTokenMissing() {
        ReflectionTestUtils.setField(util, "easyImageUrl", "");
        FileVO fileVO = buildRequest();

        assertThatThrownBy(() -> util.saveFile(fileVO))
                .isInstanceOf(PoetryRuntimeException.class)
                .hasMessageContaining("未正确配置");
    }

    @Test
    void saveFileShouldFailWhenFileVoOrFileIsNull() {
        assertThatThrownBy(() -> util.saveFile(null))
                .isInstanceOf(PoetryRuntimeException.class)
                .hasMessageContaining("文件参数不能为空");

        FileVO empty = new FileVO();
        assertThatThrownBy(() -> util.saveFile(empty))
                .isInstanceOf(PoetryRuntimeException.class)
                .hasMessageContaining("文件参数不能为空");
    }

    @Test
    void deleteFilesShouldReturnEmptyForNullOrEmptyInput() {
        assertThat(util.deleteFiles(null)).isEmpty();
        assertThat(util.deleteFiles(List.of())).isEmpty();
    }

    @Test
    void deleteFilesShouldReturnFailedWhenStorageKeyNotHttp() {
        StorageResourceRef ref = new StorageResourceRef(
                1, "https://easy.example.com/i/2024/abc.png",
                "not-a-url", "abc.png", 128L, null, "image/png"
        );

        List<StorageDeleteResult> results = util.deleteFiles(List.of(ref));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.success()).isFalse();
            assertThat(result.missing()).isFalse();
            assertThat(result.message()).contains("缺少删除凭证");
        });
    }

    @Test
    void deleteFilesShouldReturnFailedWhenStorageKeyBlank() {
        StorageResourceRef ref = new StorageResourceRef(
                1, "https://easy.example.com/i/2024/abc.png",
                null, "abc.png", 128L, null, "image/png"
        );

        List<StorageDeleteResult> results = util.deleteFiles(List.of(ref));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("缺少删除凭证");
        });
    }

    @Test
    void deleteFilesShouldReturnMissingOn404() {
        String delUrl = "https://easy.example.com/del?hash=abc-token";
        StorageResourceRef ref = new StorageResourceRef(
                1, "https://easy.example.com/i/2024/abc.png",
                delUrl, "abc.png", 128L, null, "image/png"
        );
        HttpClientErrorException notFound = HttpClientErrorException.NotFound.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null
        );
        when(restTemplate.getForObject(delUrl, String.class)).thenThrow(notFound);

        List<StorageDeleteResult> results = util.deleteFiles(List.of(ref));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.success()).isFalse();
            assertThat(result.missing()).isTrue();
            assertThat(result.message()).contains("物理文件不存在");
        });
        verify(restTemplate).getForObject(delUrl, String.class);
    }

    @Test
    void deleteFilesShouldReturnFailedOnOtherException() {
        String delUrl = "https://easy.example.com/del?hash=abc-token";
        StorageResourceRef ref = new StorageResourceRef(
                1, "https://easy.example.com/i/2024/abc.png",
                delUrl, "abc.png", 128L, null, "image/png"
        );
        when(restTemplate.getForObject(delUrl, String.class))
                .thenThrow(new RuntimeException("连接超时"));

        List<StorageDeleteResult> results = util.deleteFiles(List.of(ref));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.success()).isFalse();
            assertThat(result.missing()).isFalse();
            assertThat(result.message()).contains("连接超时");
        });
    }

    @Test
    void deleteFilesShouldReturnDeletedOnSuccess() {
        String delUrl = "https://easy.example.com/del?hash=abc-token";
        StorageResourceRef ref = new StorageResourceRef(
                1, "https://easy.example.com/i/2024/abc.png",
                delUrl, "abc.png", 128L, null, "image/png"
        );
        when(restTemplate.getForObject(delUrl, String.class)).thenReturn("ok");

        List<StorageDeleteResult> results = util.deleteFiles(List.of(ref));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.success()).isTrue();
            assertThat(result.missing()).isFalse();
        });
        verify(restTemplate).getForObject(delUrl, String.class);
    }

    @Test
    void deleteFilesShouldHandleMixedResultsInOrder() {
        StorageResourceRef ok = new StorageResourceRef(
                1, "https://easy.example.com/i/2024/ok.png",
                "https://easy.example.com/del?hash=ok", "ok.png", 128L, null, "image/png"
        );
        StorageResourceRef missing = new StorageResourceRef(
                2, "https://easy.example.com/i/2024/missing.png",
                "https://easy.example.com/del?hash=missing", "missing.png", 128L, null, "image/png"
        );
        StorageResourceRef noCred = new StorageResourceRef(
                3, "https://easy.example.com/i/2024/nocred.png",
                "bad-key", "nocred.png", 128L, null, "image/png"
        );
        HttpClientErrorException notFound = HttpClientErrorException.NotFound.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null
        );
        when(restTemplate.getForObject("https://easy.example.com/del?hash=ok", String.class))
                .thenReturn("ok");
        when(restTemplate.getForObject("https://easy.example.com/del?hash=missing", String.class))
                .thenThrow(notFound);

        List<StorageDeleteResult> results = util.deleteFiles(List.of(ok, missing, noCred));

        assertThat(results).hasSize(3);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(1).missing()).isTrue();
        assertThat(results.get(2).success()).isFalse();
        assertThat(results.get(2).message()).contains("缺少删除凭证");
    }

    @Test
    void getCapabilityShouldReflectEnabledStateWhenFullyConfigured() {
        StorageCapability capability = util.getCapability();

        assertThat(capability.storeType()).isEqualTo(StoreEnum.EASYIMAGE.getCode());
        assertThat(capability.enabled()).isTrue();
        assertThat(capability.readSupported()).isTrue();
        assertThat(capability.uploadSupported()).isTrue();
        assertThat(capability.deleteSupported()).isTrue();
        assertThat(capability.verifySupported()).isTrue();
        assertThat(capability.acceptedMimePrefixes()).containsExactly("image/");
    }

    @Test
    void getCapabilityShouldReportDisabledWhenNotEnabled() {
        ReflectionTestUtils.setField(util, "easyImageEnable", false);

        StorageCapability capability = util.getCapability();

        assertThat(capability.enabled()).isFalse();
        assertThat(capability.readSupported()).isFalse();
    }

    @Test
    void getCapabilityShouldReportDisabledWhenTokenMissing() {
        ReflectionTestUtils.setField(util, "easyImageToken", "");

        StorageCapability capability = util.getCapability();

        assertThat(capability.enabled()).isFalse();
        assertThat(capability.readSupported()).isFalse();
    }

    @Test
    void getCapabilityShouldReportDisabledWhenNoTrustedHost() {
        when(trustedRemoteStorageReader.parseTrustedHosts(EASYIMAGE_URL, TRUSTED_HOST))
                .thenReturn(Set.of());

        StorageCapability capability = util.getCapability();

        assertThat(capability.enabled()).isTrue();
        assertThat(capability.readSupported()).isFalse();
    }

    private FileVO buildRequest() {
        FileVO fileVO = new FileVO();
        MultipartFile file = mock(MultipartFile.class);
        // 用 lenient 是因为部分测试（禁用、缺配置）在参数校验后直接抛异常，不会触达 file 的方法调用
        lenient().when(file.getResource()).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));
        lenient().when(file.getOriginalFilename()).thenReturn("test.png");
        fileVO.setFile(file);
        fileVO.setType("image");
        fileVO.setOriginalName("test.png");
        fileVO.setResourceHash("a".repeat(64));
        fileVO.setCreateOnly(true);
        return fileVO;
    }

    /**
     * EasyImageResponse 是 EasyImageUtil 的私有静态内部类，用反射构造实例以便注入到 mock 的 RestTemplate 返回值。
     */
    private Object buildEasyImageResponse(String result, int code, String url, String del) throws Exception {
        Class<?> clazz = Class.forName("com.ld.poetry.utils.storage.EasyImageUtil$EasyImageResponse");
        Constructor<?> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();
        invokeSetter(clazz, instance, "setResult", String.class, result);
        invokeSetter(clazz, instance, "setCode", int.class, code);
        invokeSetter(clazz, instance, "setUrl", String.class, url);
        invokeSetter(clazz, instance, "setDel", String.class, del);
        return instance;
    }

    private void invokeSetter(Class<?> clazz, Object instance, String setter, Class<?> type, Object value)
            throws Exception {
        if (value == null) {
            return;
        }
        Method method = clazz.getDeclaredMethod(setter, type);
        method.setAccessible(true);
        method.invoke(instance, value);
    }
}
