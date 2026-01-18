package io.github.fracture_hikari.maid_agent.ai;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.*;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import io.github.fracture_hikari.maid_agent.MaidAgent;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Utility to trigger autonomous AI chat for task completion notifications.
 * This allows behaviors to notify the LLM when async tasks complete.
 */
public class AIChatCallback {

    /**
     * Trigger an autonomous AI chat with a system message about task completion.
     * This will start a new conversation with the LLM containing the result.
     * 
     * @param maid The maid entity
     * @param systemMessage A message describing what happened (e.g., "I fetched 5 diamonds")
     */
    public static void notifyTaskComplete(EntityMaid maid, String systemMessage) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Get the maid's AI chat manager via reflection (it's not publicly accessible)
        MaidAIChatManager chatManager = getMaidAIChatManager(maid);
        if (chatManager == null) {
            MaidAgent.LOGGER.warn("Could not get MaidAIChatManager for task completion notification");
            return;
        }

        @Nullable LLMSite site = chatManager.getLLMSite();
        if (site == null || !site.enabled()) {
            // No LLM configured, just show chat bubble
            maid.getChatBubbleManager().addTextChatBubble(systemMessage);
            return;
        }

        serverLevel.getServer().submit(() -> {
            try {
                triggerAutonomousChat(maid, chatManager, site, systemMessage);
            } catch (Exception e) {
                MaidAgent.LOGGER.error("Failed to trigger autonomous chat", e);
                // Fallback to chat bubble
                maid.getChatBubbleManager().addTextChatBubble(systemMessage);
            }
        });
    }

    private static void triggerAutonomousChat(EntityMaid maid, MaidAIChatManager chatManager, 
                                               LLMSite site, String systemMessage) {
        LLMClient chatClient = site.client();
        
        // Build message list with task result as a "user" message (simulating task report)
        List<LLMMessage> messages = Lists.newArrayList();
        
        // Add system prompt (simplified - task result notification)
        String systemPrompt = """
            You are a maid assistant. A background task has just completed.
            Report the result to your master naturally in a conversational way.
            Keep your response brief and friendly.
            """;
        messages.add(LLMMessage.systemChat(maid, systemPrompt));
        
        // Add the task result as user input (this is what the maid should respond to)
        messages.add(LLMMessage.userChat(maid, "[Task Completed] " + systemMessage));
        
        // Create chat config
        LLMConfig config = LLMConfig.normalChat(chatManager.getLLMModel(), maid);
        
        // Add thinking bubble
        ChatBubbleManager bubbleManager = maid.getChatBubbleManager();
        long key = bubbleManager.addThinkingText("ai.touhou_little_maid.chat.chat_bubble_waiting");
        
        // Create callback - using autonomous message pattern
        LLMCallback callback = new LLMCallback(chatManager, systemMessage, key);
        
        // Trigger the chat
        chatClient.chat(messages, config, callback);
    }

    @Nullable
    private static MaidAIChatManager getMaidAIChatManager(EntityMaid maid) {
        try {
            // MaidAIChatManager is stored in EntityMaid but may not have a public getter
            // Try to find it via reflection
            for (Field field : EntityMaid.class.getDeclaredFields()) {
                if (MaidAIChatManager.class.isAssignableFrom(field.getType()) ||
                    MaidAIChatData.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(maid);
                    if (value instanceof MaidAIChatManager manager) {
                        return manager;
                    }
                }
            }
            
            // Try getAIChatManager if it exists
            try {
                var method = EntityMaid.class.getMethod("getAIChatManager");
                return (MaidAIChatManager) method.invoke(maid);
            } catch (NoSuchMethodException ignored) {}
            
            // Try creating a new one (may not have history though)
            return new MaidAIChatManager(maid);
            
        } catch (Exception e) {
            MaidAgent.LOGGER.error("Failed to get MaidAIChatManager", e);
            return null;
        }
    }
}
