package me.guardian.client.screen;

import me.guardian.network.TriggerAreaPayloads;
import me.guardian.trigger.TriggerArea;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class TriggerAreaEditorScreen extends Screen {
    private final TriggerArea area;
    private final List<String> commandValues;
    private final List<EditBox> commandFields = new ArrayList<>();
    private EditBox triggerSelectors;
    private EditBox restrictionSelectors;
    private boolean runOnce;
    private String triggerMode;
    private boolean globalRestrictions;
    private String restrictionMode;
    private final List<CommandSuggestions> commandSuggestions = new ArrayList<>();
    private Button runButton;
    private Button triggerButton;
    private Button globalButton;
    private Button restrictionButton;
    private Button addCommandButton;

    public TriggerAreaEditorScreen(TriggerArea area) {
        super(Component.translatable("screen.guardian_mod.trigger_area"));
        this.area = area;
        this.commandValues = new ArrayList<>(area.commands.isEmpty() ? List.of("") : area.commands);
        this.runOnce = area.runOnce;
        this.triggerMode = area.triggerMode;
        this.globalRestrictions = area.globalRestrictions;
        this.restrictionMode = area.restrictionMode;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(600, width - 40);
        int left = (width - panelWidth) / 2;
        int top = 28;
        commandFields.clear();
        commandSuggestions.clear();
        int commandFieldWidth = panelWidth - 34;
        for (int i = 0; i < commandValues.size(); i++) {
            EditBox commandField = new EditBox(font, left, top + 28 + i * 24, commandFieldWidth, 20,
                    Component.translatable("screen.guardian_mod.trigger_area.console_command"));
            commandField.setMaxLength(32767);
            commandField.setValue(commandValues.get(i));
            commandField.setResponder(value -> updateCommandSuggestions(commandField));
            commandFields.add(commandField);
            addRenderableWidget(commandField);
            CommandSuggestions suggestions = new CommandSuggestions(minecraft, this, commandField, font,
                    true, true, 0, 7, false, 0x80000000);
            suggestions.setAllowSuggestions(true);
            suggestions.updateCommandInfo();
            commandSuggestions.add(suggestions);
        }
        addCommandButton = addRenderableWidget(Button.builder(Component.translatable("button.guardian_mod.add_command"), button -> addCommandField())
                .bounds(left + commandFieldWidth + 6, top + 28, 28, 20)
                .build());

        int controlsTop = top + 28 + commandValues.size() * 24 + 28;

        int buttonWidth = (panelWidth - 20) / 3;
        runButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            runOnce = !runOnce;
            updateLabels();
        }).bounds(left, controlsTop, buttonWidth, 20).build());

        triggerButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            triggerMode = "everyone".equals(triggerMode) ? "selectors" : "everyone";
            updateLabels();
        }).bounds(left + buttonWidth + 10, controlsTop, buttonWidth, 20).build());

        globalButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            globalRestrictions = !globalRestrictions;
            updateLabels();
        }).bounds(left + (buttonWidth + 10) * 2, controlsTop, buttonWidth, 20).build());

        triggerSelectors = new EditBox(font, left, controlsTop + 32, panelWidth, 20,
                Component.translatable("screen.guardian_mod.trigger_area.selectors"));
        triggerSelectors.setMaxLength(32767);
        triggerSelectors.setValue(area.triggerSelectors);
        addRenderableWidget(triggerSelectors);

        restrictionButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            restrictionMode = switch (restrictionMode) {
                case "everyone" -> "only_matching";
                case "only_matching" -> "except_matching";
                default -> "everyone";
            };
            updateLabels();
        }).bounds(left, controlsTop + 62, 190, 20).build());

        restrictionSelectors = new EditBox(font, left, controlsTop + 92, panelWidth, 20,
                Component.translatable("screen.guardian_mod.trigger_area.restriction_selectors"));
        restrictionSelectors.setMaxLength(32767);
        restrictionSelectors.setValue(area.restrictionSelectors);
        addRenderableWidget(restrictionSelectors);

        int bottom = Math.min(height - 36, controlsTop + 124);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save()).bounds(left, bottom, 148, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(left + 158, bottom, 148, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("button.guardian_mod.delete"), button -> delete()).bounds(left + panelWidth - 148, bottom, 148, 20).build());
        updateLabels();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(graphics);
        Component heading = Component.translatable("screen.guardian_mod.trigger_area.title");
        graphics.drawString(font, heading, width / 2 - font.width(heading) / 2, 10, 0xFFFFFF);
        if (!commandFields.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.guardian_mod.trigger_area.console_command"),
                    commandFields.get(0).getX(), commandFields.get(0).getY() - 11, 0xA0A0A0);
        }
        if (triggerSelectors.isVisible()) {
            graphics.drawString(font, Component.translatable("screen.guardian_mod.trigger_area.selectors"),
                    triggerSelectors.getX(), triggerSelectors.getY() - 10, 0xA0A0A0);
        }
        if (restrictionSelectors.isVisible()) {
            graphics.drawString(font, Component.translatable("screen.guardian_mod.trigger_area.restriction_selectors"),
                    restrictionSelectors.getX(), restrictionSelectors.getY() - 10, 0xA0A0A0);
        }
        super.render(graphics, mouseX, mouseY, delta);
        CommandSuggestions suggestions = activeCommandSuggestions();
        if (suggestions != null) {
            suggestions.render(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        CommandSuggestions suggestions = activeCommandSuggestions();
        if (suggestions != null && suggestions.keyPressed(event)) {
            return true;
        }
        boolean handled = super.keyPressed(event);
        if (handled) {
            updateActiveCommandSuggestions();
        }
        return handled;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        CommandSuggestions suggestions = activeCommandSuggestions();
        if (suggestions != null && suggestions.mouseClicked(event)) {
            return true;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (handled) {
            updateActiveCommandSuggestions();
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        CommandSuggestions suggestions = activeCommandSuggestions();
        if (suggestions != null && suggestions.mouseScrolled(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void save() {
        saveCommandValues();
        List<String> commands = commandValues.stream()
                .map(String::trim)
                .filter(command -> !command.isEmpty())
                .toList();
        TriggerArea edited = area.edited(commands, runOnce, triggerMode, triggerSelectors.getValue(),
                globalRestrictions, restrictionMode, restrictionSelectors.getValue());
        ClientPlayNetworking.send(new TriggerAreaPayloads.SaveEditor(edited.serialize()));
        onClose();
    }

    private void delete() {
        ClientPlayNetworking.send(new TriggerAreaPayloads.Delete(area.id));
        onClose();
    }

    private void addCommandField() {
        saveCommandValues();
        commandValues.add("");
        rebuildWidgets();
    }

    private void saveCommandValues() {
        commandValues.clear();
        for (EditBox field : commandFields) {
            commandValues.add(field.getValue());
        }
    }

    private void updateLabels() {
        runButton.setMessage(Component.translatable(runOnce ? "button.guardian_mod.trigger.run_once" : "button.guardian_mod.trigger.run_every_time"));
        triggerButton.setMessage(Component.translatable("button.guardian_mod.trigger.triggered_by",
                Component.translatable("button.guardian_mod.trigger.mode." + ("everyone".equals(triggerMode) ? "everyone" : "selectors"))));
        globalButton.setMessage(Component.translatable(globalRestrictions ? "button.guardian_mod.trigger.global_restricted" : "button.guardian_mod.trigger.global_off"));
        restrictionButton.setMessage(Component.translatable("button.guardian_mod.trigger.restrict",
                Component.translatable(switch (restrictionMode) {
                    case "only_matching" -> "button.guardian_mod.trigger.restrict.only_selectors";
                    case "except_matching" -> "button.guardian_mod.trigger.restrict.except_selectors";
                    default -> "button.guardian_mod.trigger.restrict.everyone";
                })));
        triggerSelectors.setVisible(!"everyone".equals(triggerMode));
        restrictionSelectors.setVisible(globalRestrictions && !"everyone".equals(restrictionMode));
    }

    private CommandSuggestions activeCommandSuggestions() {
        for (int i = 0; i < commandFields.size(); i++) {
            EditBox field = commandFields.get(i);
            if (field.isFocused()) {
                return i < commandSuggestions.size() ? commandSuggestions.get(i) : null;
            }
        }
        return null;
    }

    private void updateActiveCommandSuggestions() {
        CommandSuggestions suggestions = activeCommandSuggestions();
        if (suggestions != null) {
            suggestions.updateCommandInfo();
        }
    }

    private void updateCommandSuggestions(EditBox field) {
        for (int i = 0; i < commandFields.size(); i++) {
            if (commandFields.get(i) == field && i < commandSuggestions.size()) {
                commandSuggestions.get(i).updateCommandInfo();
                return;
            }
        }
    }
}
