package io.github.fracture_hikari.maid_agent.ai.service.llm.gemini;

import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializableSite;
import com.github.tartaricacid.touhoulittlemaid.ai.service.SupportModelSelect;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMSite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.fracture_hikari.maid_agent.MaidAgent;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gemini API site configuration.
 * Implements LLMSite and SupportModelSelect for Gemini model integration.
 */
public final class LLMGeminiSite implements LLMSite, SupportModelSelect {
    public static final String API_TYPE = "gemini";

    private final String id;
    private final ResourceLocation icon;
    private final Map<String, String> headers;
    private final Map<String, String> models;

    private String url;
    private boolean enabled;
    private String secretKey;

    public LLMGeminiSite(String id, ResourceLocation icon, String url, boolean enabled,
            String secretKey, Map<String, String> headers, Map<String, String> models) {
        this.id = id;
        this.icon = icon;
        this.url = url;
        this.enabled = enabled;
        this.secretKey = secretKey;
        this.headers = headers;
        this.models = models;
    }

    public LLMGeminiSite(String id, ResourceLocation icon, String url, boolean enabled,
            String secretKey, Map<String, String> headers, List<String> models) {
        this(id, icon, url, enabled, secretKey, headers,
                models.stream().collect(Collectors.toMap(Function.identity(), Function.identity())));
    }

    @Override
    public String getApiType() {
        return API_TYPE;
    }

    @Override
    public LLMClient client() {
        return new LLMGeminiClient(LLM_HTTP_CLIENT, this);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ResourceLocation icon() {
        return icon;
    }

    @Override
    public String url() {
        return url;
    }

    public String secretKey() {
        return secretKey;
    }

    @Override
    public Map<String, String> headers() {
        return headers;
    }

    @Override
    public Map<String, String> models() {
        return models;
    }

    public void addModel(String model) {
        this.addModel(model, model);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public static class Serializer implements SerializableSite<LLMGeminiSite> {
        private static final Codec<Map<String, String>> MODELS_CODEC = Codec.list(Codec.STRING).xmap(
                list -> list.stream().collect(Collectors.toMap(Function.identity(), Function.identity())),
                map -> map.keySet().stream().toList());

        public static final Codec<LLMGeminiSite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf(ID).forGetter(LLMGeminiSite::id),
                ResourceLocation.CODEC.fieldOf(ICON).forGetter(LLMGeminiSite::icon),
                Codec.STRING.fieldOf(URL).forGetter(LLMGeminiSite::url),
                Codec.BOOL.fieldOf(ENABLED).forGetter(LLMGeminiSite::enabled),
                Codec.STRING.fieldOf(SECRET_KEY).forGetter(LLMGeminiSite::secretKey),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf(HEADERS).forGetter(LLMGeminiSite::headers),
                MODELS_CODEC.fieldOf(MODELS).forGetter(LLMGeminiSite::models)).apply(instance, LLMGeminiSite::new));

        @Override
        public LLMGeminiSite defaultSite() {
            return new LLMGeminiSite(API_TYPE,
                    ResourceLocation.fromNamespaceAndPath(MaidAgent.MODID, "textures/gui/ai_chat/gemini.png"),
                    "https://generativelanguage.googleapis.com/v1beta/models", false,
                    StringUtils.EMPTY, Map.of(),
                    List.of("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-2.5-flash", "gemini-2.5-flash-lite"));
        }

        @Override
        public Codec<LLMGeminiSite> codec() {
            return CODEC;
        }
    }
}
