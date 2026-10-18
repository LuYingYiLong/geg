package io.github.luyingyilong.geg.chat.client;

import com.mojang.brigadier.suggestion.Suggestion;
import io.github.luyingyilong.geg.chat.ChatPolicy;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.input.TextArea;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Stateful multiline composer. Commands use the connection's ordinary signed command path.
 */
public final class ChatComposer extends Container {
    private final brachy.modularui.value.StringValue inputValue = new brachy.modularui.value.StringValue("");
    private final TextArea input = new TextArea(210).rows(1);
    private final InputControl editor = new InputControl(input);
    private final Box suggestionButtons = new Box().gap(0);
    private final Completion completion = new Completion();
    private List<Suggestion> suggestions = List.of();
    private String completionSource = "", suppressedSource = "";
    private int completionCursor = -1, suggestionIndex;
    private long completionRevision;
    private io.github.luyingyilong.geg.chat.ChatChannel bound;
    private String displayedDraft = "";
    private Runnable afterSend = () -> {
    };
    private Runnable onEmptySubmit = () -> {
    };
    private boolean editorInitialized;

    public ChatComposer() {
        input.value(inputValue);
        input.setMaxLength(1024);
        input.placeholder(Component.translatable("chat.geg.compose_hint"));
        input.onSubmit(this::submit).keyHandler(this::key);
        editor.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        addChild(editor);
        addChild(completion);
        sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        completion.visible(false);
    }

    public TextArea input() {
        return input;
    }

    public InputControl editor() {
        return editor;
    }

    public ChatComposer afterSend(Runnable action) {
        afterSend = action;
        return this;
    }

    public ChatComposer onEmptySubmit(Runnable action) {
        onEmptySubmit = java.util.Objects.requireNonNull(action);
        return this;
    }

    public void bind() {
        var selected = ClientChatState.selected();
        if (!selected.equals(bound)) {
            store();
            bound = selected;
            displayedDraft = ClientChatState.view(bound).draft;
            inputValue.setStringValue(displayedDraft);
            input.setText(displayedDraft);
            input.cursorOffset(displayedDraft.length());
            dismissSuggestions();
        }
    }

    public void store() {
        if (bound != null) {
            ClientChatState.view(bound).draft = input.getText();
            displayedDraft = input.getText();
            inputValue.setStringValue(displayedDraft);
        }
    }

    public void update() {
        if (!editorInitialized) {
            // MUI initializes its StringValue after this tree is assembled and resets the caret.
            input.cursorOffset(input.getText().length());
            editorInitialized = true;
        }
        bind();
        var view = ClientChatState.view(bound);
        // A server acknowledgement may have cleared this draft while the widget still shows the sent text.
        if (input.getText().equals(displayedDraft) && !view.draft.equals(displayedDraft)) {
            input.setText(view.draft);
            displayedDraft = view.draft;
            inputValue.setStringValue(displayedDraft);
        } else store();
        updateSuggestions();
        completion.visible(suggestionsVisible());
    }

    private void submit() {
        store();
        var view = ClientChatState.view(ClientChatState.selected());
        String value = input.getText();
        if (input.composing()) return;
        if (value.isBlank()) {
            dismissSuggestions();
            onEmptySubmit.run();
            return;
        }
        if (!view.available || ClientChatState.pending(ClientChatState.selected())) return;
        if (value.startsWith("/")) {
            if (value.length() <= 1 || value.substring(1).isBlank() || value.length() > 256 || value.contains("\n")) {
                view.status = "chat.geg.invalid_command";
                return;
            }
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.sendCommand(value.substring(1));
                view.draft = "";
                input.setText("");
                displayedDraft = "";
                Minecraft.getInstance().gui.getChat().addRecentChat(value);
                dismissSuggestions();
                afterSend.run();
            }
        } else if (ChatPolicy.valid(ChatPolicy.normalize(value))) {
            ClientChatState.send(value);
            afterSend.run();
        } else view.status = "chat.geg.invalid_message";
    }

    private void updateSuggestions() {
        String value = input.getText();
        int caret = input.cursorOffset();
        if (!input.isFocused() || input.composing() || !value.startsWith("/") || value.contains("\n") || value.equals(suppressedSource)) {
            if (completion.visible()) completion.visible(false);
            return;
        }
        if (value.equals(completionSource) && caret == completionCursor) return;
        suggestions = List.of();
        completion.visible(false);
        completionSource = value;
        completionCursor = caret;
        long token = ++completionRevision;
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        String command = value.substring(1);
        int offset = Math.max(0, Math.min(command.length(), caret - 1));
        var parsed = connection.getCommands().parse(command, connection.getSuggestionsProvider());
        connection.getCommands().getCompletionSuggestions(parsed, offset).thenAccept(result -> Minecraft.getInstance().execute(() -> {
            if (token != completionRevision || !input.isFocused() || !input.getText().equals(value)) return;
            suggestions = result.getList().stream().limit(8).toList();
            suggestionIndex = 0;
            for (Control child : suggestionButtons.children()) suggestionButtons.removeChild(child);
            for (Suggestion suggestion : suggestions) {
                Button button = new Button(Component.literal(suggestion.getText()).withStyle(style -> style.withColor(OreTheme.TEXT_WHITE))).type(Button.Type.TEXT)
                        .toggleButton(true).alignment(Button.TextAlignment.LEFT).onPressed(() -> apply(suggestion));
                button.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
                suggestionButtons.add(button);
            }
            // Suggestions stay in a non-modal overlay, so the native editor retains keyboard focus.
        }));
    }

    public List<Suggestion> suggestions() {
        return suggestions;
    }

    public int suggestionIndex() {
        return suggestionIndex;
    }

    public boolean suggestionsVisible() {
        return input.isFocused() && !input.composing() && !suggestions.isEmpty()
                && input.getText().equals(completionSource) && !input.getText().equals(suppressedSource);
    }

    private boolean key(int key, int modifiers) {
        if (!suggestionsVisible()) return false;
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0)
            return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            dismissSuggestions();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            suggestionIndex = Math.floorMod(suggestionIndex + (key == GLFW.GLFW_KEY_UP ? -1 : 1), suggestions.size());
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            apply(suggestions.get(suggestionIndex));
            return true;
        }
        return false;
    }

    public boolean handleKey(int key, int modifiers) {
        return key(key, modifiers);
    }

    public void apply(Suggestion suggestion) {
        String command = input.getText().substring(1);
        String result = suggestion.apply(command);
        input.applyCompletion("/" + result, 1 + suggestion.getRange().getStart() + suggestion.getText().length());
        store();
        dismissSuggestions();
        afterSend.run();
    }

    public void dismissSuggestions() {
        suppressedSource = input.getText();
        suggestions = List.of();
        completion.visible(false);
        completionRevision++;
    }

    @Override
    public Size measureSize(Font font, Size available) {
        return editor.measureSize(font, available);
    }

    @Override
    public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(editor, new Rect(0, 0, bounds.width(), bounds.height()), font);
        if (completion.visible()) {
            int height = Math.min(132, suggestionButtons.measureSize(font, new Size(bounds.width(), 200)).height() + 2);
            layoutChild(completion, new Rect(0, -height - 2, bounds.width(), height), font);
        }
    }

    private final class Completion extends Container {
        private final Panel background = new Panel(OreTheme.PANEL)
                .child(new Padding(Padding.Insets.all(1)).child(new Scroll().horizontal(Scroll.BarMode.DISABLED).content(suggestionButtons)));

        Completion() {
            addChild(background);
        }

        @Override
        public float contentDepth() {
            return 50;
        }

        @Override
        public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            fitChild(background, new Rect(0, 0, bounds.width(), bounds.height()), font);
            int i = 0;
            for (Control node : suggestionButtons.children())
                if (node instanceof Button button)
                    button.buttonPressedNoSignal(i++ == suggestionIndex);
        }

        @Override
        public void draw(brachy.modularui.screen.viewport.ModularGuiContext context, int x, int y) {
            var graphics = context.getGraphics();
            graphics.flush();
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, 0, 50);
                super.draw(context, x, y);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }
}
