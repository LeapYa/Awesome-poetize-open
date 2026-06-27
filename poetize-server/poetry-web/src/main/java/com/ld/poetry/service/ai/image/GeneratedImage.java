package com.ld.poetry.service.ai.image;

/**
 * 生图结果 DTO，支持两种返回形态：
 * <ul>
 *   <li>URL 形态（OpenAI/SiliconFlow/豆包/DashScope）：{@link #url} 有值，{@link #imageBytes} 为 null</li>
 *   <li>字节形态（Gemini）：{@link #imageBytes} 有值 + {@link #mimeType}，{@link #url} 为 null</li>
 * </ul>
 */
public class GeneratedImage {

    private final String url;
    private final byte[] imageBytes;
    private final String mimeType;
    private final String provider;
    private final String model;

    private GeneratedImage(String url, byte[] imageBytes, String mimeType, String provider, String model) {
        this.url = url;
        this.imageBytes = imageBytes;
        this.mimeType = mimeType;
        this.provider = provider;
        this.model = model;
    }

    public static GeneratedImage ofUrl(String url, String provider, String model) {
        return new GeneratedImage(url, null, null, provider, model);
    }

    public static GeneratedImage ofBytes(byte[] imageBytes, String mimeType, String provider, String model) {
        return new GeneratedImage(null, imageBytes, mimeType, provider, model);
    }

    public boolean hasBytes() {
        return imageBytes != null && imageBytes.length > 0;
    }

    public boolean hasUrl() {
        return url != null && !url.isBlank();
    }

    public String getUrl() { return url; }
    public byte[] getImageBytes() { return imageBytes; }
    public String getMimeType() { return mimeType; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
}
