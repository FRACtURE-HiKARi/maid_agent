package io.github.fracture_hikari.maid_agent.ai.service.llm.claude;

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
 * Claude API site configuration.
 * Implements LLMSite and SupportModelSelect for Claude model integration.
 */
public final class LLMClaudeSite implements LLMSite, SupportModelSelect {
    public static final String API_TYPE = "claude";

    private final String id;
    private final ResourceLocation icon;
    private final Map<String, String> headers;
    private final Map<String, String> models;

    private String url;
    private boolean enabled;
    private String secretKey;

    public LLMClaudeSite(String id, ResourceLocation icon, String url, boolean enabled,
                         String secretKey, Map<String, String> headers, Map<String, String> models) {
        this.id = id;
        this.icon = icon;
        this.url = url;
        this.enabled = enabled;
        this.secretKey = secretKey;
        this.headers = headers;
        this.models = models;
    }

    public LLMClaudeSite(String id, ResourceLocation icon, String url, boolean enabled,
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
        return new LLMClaudeClient(LLM_HTTP_CLIENT, this);
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

    public static class Serializer implements SerializableSite<LLMClaudeSite> {
        private static final Codec<Map<String, String>> MODELS_CODEC = Codec.list(Codec.STRING).xmap(
                list -> list.stream().collect(Collectors.toMap(Function.identity(), Function.identity())),
                map -> map.keySet().stream().toList());

        public static final Codec<LLMClaudeSite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf(ID).forGetter(LLMClaudeSite::id),
                ResourceLocation.CODEC.fieldOf(ICON).forGetter(LLMClaudeSite::icon),
                Codec.STRING.fieldOf(URL).forGetter(LLMClaudeSite::url),
                Codec.BOOL.fieldOf(ENABLED).forGetter(LLMClaudeSite::enabled),
                Codec.STRING.fieldOf(SECRET_KEY).forGetter(LLMClaudeSite::secretKey),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf(HEADERS).forGetter(LLMClaudeSite::headers),
                MODELS_CODEC.fieldOf(MODELS).forGetter(LLMClaudeSite::models)
        ).apply(instance, LLMClaudeSite::new));

        @Override
        public LLMClaudeSite defaultSite() {
            return new LLMClaudeSite(API_TYPE,
                    ResourceLocation.fromNamespaceAndPath(MaidAgent.MODID, "textures/gui/ai_chat/claude.png"),
                    "https://api.anthropic.com/v1/messages", false,
                    StringUtils.EMPTY, Map.of(),
                    List.of("claude-sonnet-4-20250514", "claude-3-5-sonnet-20241022", 
                            "claude-3-5-haiku-20241022", "claude-3-opus-20240229"));
        }

        @Override
        public Codec<LLMClaudeSite> codec() {
            return CODEC;
        }
    }
}
