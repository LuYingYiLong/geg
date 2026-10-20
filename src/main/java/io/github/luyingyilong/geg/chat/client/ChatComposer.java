package io.github.luyingyilong.geg.chat.client;

import io.github.luyingyilong.ore.signal.Signals;

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
    private final TextArea input = new TextArea(210).setRows(1);
    private final InputControl editor = new InputControl(input);
    private final Box suggestionButtons = new Box().setGap(0);
    private final Completion completion = new Completion();
    private List<Suggestion> suggestions = List.of();
    private String completionSource = "", suppressedSource = "";
    private int completionCursor = -1, suggestionIndex;
    private long completionRevision;
    private io.github.luyingyilong.geg.chat.ChatChannel bound;
    private String displayedDraft = "";
    private Runnable afterSend = () -> {
    };
    private final io.github.luyingyilong.ore.signal.SignalSource0 emptySubmitted =
            new io.github.luyingyilong.ore.signal.SignalSource0(this, "emptySubmitted");
    private boolean editorInitialized;

    public ChatComposer() {
        input.value(inputValue);
        input.setMaxLength(1024);
        input.setPlaceholder(Component.translatable("chat.geg.compose_hint"));
        Signals.configure(input, connected -> { connected.setSubmitOnEnter(true); connected.submitted().connect(connected, submittedText130 -> this.submit()); }).setKeyHandler(this::key);
        editor.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        this.addChild(editor);
        this.addChild(completion);
        setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        completion.setVisible(false);
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

    public io.github.luyingyilong.ore.signal.Signal0 emptySubmitted() { return emptySubmitted.signal(); }

    public void bind() {
        var selected = ClientChatState.selected();
        if (!selected.equals(bound)) {
            store();
            bound = selected;
            displayedDraft = ClientChatState.view(bound).draft;
            inputValue.setStringValue(displayedDraft);
            input.setTextNoSignal(displayedDraft);
            input.setCursorOffset(displayedDraft.length());
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
            input.setCursorOffset(input.getText().length());
            editorInitialized = true;
        }
        bind();
        var view = ClientChatState.view(bound);
        // A server acknowledgement may have cleared this draft while the widget still shows the sent text.
        if (input.getText().equals(displayedDraft) && !view.draft.equals(displayedDraft)) {
            input.setTextNoSignal(view.draft);
            displayedDraft = view.draft;
            inputValue.setStringValue(displayedDraft);
        } else store();
        updateSuggestions();
        completion.setVisible(suggestionsVisible());
    }

    private void submit() {
        store();
        var view = ClientChatState.view(ClientChatState.selected());
        String value = input.getText();
        if (input.getComposing()) return;
        if (value.isBlank()) {
            dismissSuggestions();
            emptySubmitted.emit();
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
                input.setTextNoSignal("");
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
        int caret = input.getCursorOffset();
        if (!input.isFocused() || input.getComposing() || !value.startsWith("/") || value.contains("\n") || value.equals(suppressedSource)) {
            if (completion.getVisible()) completion.setVisible(false);
            return;
        }
        if (value.equals(completionSource) && caret == completionCursor) return;
        suggestions = List.of();
        completion.setVisible(false);
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
            for (Control child : suggestionButtons.getChildren()) { if (child.getParent() != null) child.getParent().removeChild(child); child.queueFree(); }
            for (Suggestion suggestion : suggestions) {
                Button button = Signals.configure(new Button(Component.literal(suggestion.getText())).setThemeTypeVariation("TextButton")
                        .setToggleButton(true).setAlignment(Button.TextAlignment.LEFT), connected -> connected.pressed().connect(connected, () -> apply(suggestion)));
                button.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
                suggestionButtons.addChild(button);
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
        return input.isFocused() && !input.getComposing() && !suggestions.isEmpty()
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
        completion.setVisible(false);
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
        if (completion.getVisible()) {
            int height = Math.min(132, suggestionButtons.measureSize(font, new Size(bounds.width(), 200)).height() + 2);
            layoutChild(completion, new Rect(0, -height - 2, bounds.width(), height), font);
        }
    }

    private final class Completion extends Container {
        private final Panel background = new Panel(OreTheme.PANEL)
                .setChild(new Padding(Padding.Insets.all(1)).setChild(new Scroll().setHorizontal(Scroll.BarMode.DISABLED).setContent(suggestionButtons)));

        Completion() {
            this.addChild(background);
        }

        @Override
        public float getContentDepth() {
            return 50;
        }

        @Override
        public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            fitChild(background, new Rect(0, 0, bounds.width(), bounds.height()), font);
            int i = 0;
            for (Control node : suggestionButtons.getChildren())
                if (node instanceof Button button)
                    button.setButtonPressedNoSignal(i++ == suggestionIndex);
        }

        @Override
        protected void drawContent(io.github.luyingyilong.ore.render.DrawContext context, int x, int y) {
            var graphics = context.getGraphics();
            graphics.flush();
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, 0, 50);
                super.drawContent(context, x, y);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }
}
