package me.guardian.client.screen;

import me.guardian.network.TriggerAreaPayloads;
import me.guardian.trigger.TriggerArea;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
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
    private final List<Integer> commandDelays;
    private final List<EditBox> commandFields = new ArrayList<>();
    private final List<EditBox> delayFields = new ArrayList<>();
    private final List<CommandSuggestions> commandSuggestions = new ArrayList<>();
    private int activeCommandIndex = -1;
    private String triggerType;
    private boolean runOnce;
    private String triggerMode;
    private String triggerSelectors;
    private String privateSelectors;
    private boolean restrictBlockBreaking;
    private boolean restrictAttacking;
    private boolean restrictInteractions;
    private Button typeButton;
    private Button runButton;
    private Button triggerModeButton;
    private Button addCommandButton;
    private EditBox triggerSelectorField;
    private EditBox privateSelectorField;

    public TriggerAreaEditorScreen(TriggerArea area) {
        super(Component.translatable("screen.guardian_mod.trigger_area"));
        this.area = area;
        this.commandValues = new ArrayList<>(area.commands.isEmpty() ? List.of("") : area.commands);
        this.commandDelays = new ArrayList<>(area.commandDelays);
        while (this.commandDelays.size() < this.commandValues.size()) {
            this.commandDelays.add(0);
        }
        this.triggerType = area.triggerType;
        this.runOnce = area.runOnce;
        this.triggerMode = area.triggerMode;
        this.triggerSelectors = area.triggerSelectors;
        this.privateSelectors = area.privateSelectors;
        this.restrictBlockBreaking = area.restrictBlockBreaking;
        this.restrictAttacking = area.restrictAttacking;
        this.restrictInteractions = area.restrictInteractions;
    }

    @Override
    protected void init() {
        commandFields.clear();
        delayFields.clear();
        commandSuggestions.clear();
        activeCommandIndex = -1;

        int panelWidth = Math.min(640, width - 40);
        int left = (width - panelWidth) / 2;
        int y = 36;

        typeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            saveCommandValues();
            triggerType = TriggerArea.TYPE_COMMANDS.equals(triggerType) ? TriggerArea.TYPE_PRIVATE : TriggerArea.TYPE_COMMANDS;
            rebuildWidgets();
        }).bounds(left, y, 190, 20).build());

        if (TriggerArea.TYPE_COMMANDS.equals(triggerType)) {
            y = initCommandControls(left, y, panelWidth);
        } else {
            y = initPrivateControls(left, y, panelWidth);
        }

        int bottom = Math.min(height - 30, y + 18);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save()).bounds(left, bottom, 148, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(left + 158, bottom, 148, 20).build());
        if (area.runOnce) {
            addRenderableWidget(Button.builder(Component.translatable("button.guardian_mod.trigger.reset"), button -> reset()).bounds(left + 316, bottom, 148, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("button.guardian_mod.delete"), button -> delete()).bounds(left + panelWidth - 148, bottom, 148, 20).build());
        updateLabels();
    }

    private int initCommandControls(int left, int y, int panelWidth) {
        runButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            runOnce = !runOnce;
            updateLabels();
        }).bounds(left + 200, y, 150, 20).build());

        triggerModeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            triggerMode = "everyone".equals(triggerMode) ? "selectors" : "everyone";
            updateLabels();
        }).bounds(left + 360, y, 220, 20).build());

        y += 42;
        int removeButtonWidth = 20;
        int addButtonWidth = 20;
        int buttonGap = 2;
        int commandFieldWidth = panelWidth - addButtonWidth - removeButtonWidth - buttonGap * 2;
        for (int i = 0; i < commandValues.size(); i++) {
            addCommandRow(left, y + i * 24, commandFieldWidth, removeButtonWidth, buttonGap, i);
        }
        addCommandButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> addCommandField())
                .bounds(left + commandFieldWidth + removeButtonWidth + buttonGap * 2, y, addButtonWidth, 20)
                .build());

        y += commandValues.size() * 24 + 18;
        triggerSelectorField = new EditBox(font, left, y + 12, panelWidth, 20,
                Component.translatable("screen.guardian_mod.trigger_area.trigger_selectors"));
        triggerSelectorField.setMaxLength(32767);
        triggerSelectorField.setValue(triggerSelectors);
        addRenderableWidget(triggerSelectorField);
        return y + 42;
    }

    private int initPrivateControls(int left, int y, int panelWidth) {
        y += 42;
        addRenderableWidget(Checkbox.builder(Component.translatable("checkbox.guardian_mod.private.block_breaking"), font)
                .pos(left, y)
                .selected(restrictBlockBreaking)
                .onValueChange((checkbox, selected) -> restrictBlockBreaking = selected)
                .build());
        addRenderableWidget(Checkbox.builder(Component.translatable("checkbox.guardian_mod.private.attacking"), font)
                .pos(left + 210, y)
                .selected(restrictAttacking)
                .onValueChange((checkbox, selected) -> restrictAttacking = selected)
                .build());
        addRenderableWidget(Checkbox.builder(Component.translatable("checkbox.guardian_mod.private.interactions"), font)
                .pos(left + 420, y)
                .selected(restrictInteractions)
                .onValueChange((checkbox, selected) -> restrictInteractions = selected)
                .build());

        y += 48;
        privateSelectorField = new EditBox(font, left, y + 12, panelWidth, 20,
                Component.translatable("screen.guardian_mod.trigger_area.private_selectors"));
        privateSelectorField.setMaxLength(32767);
        privateSelectorField.setValue(privateSelectors);
        addRenderableWidget(privateSelectorField);
        return y + 42;
    }

    private void addCommandRow(int left, int y, int commandFieldWidth, int removeButtonWidth, int buttonGap, int index) {
        int delayFieldWidth = 45;
        int gap = 5;

        EditBox delayField = new EditBox(font, left, y, delayFieldWidth, 20,
                Component.translatable("screen.guardian_mod.trigger_area.delay"));
        delayField.setMaxLength(8);
        int currentDelay = 0;
        if (index < commandDelays.size()) {
            currentDelay = commandDelays.get(index);
        }
        delayField.setValue(String.valueOf(currentDelay));
        delayField.setFilter(text -> text.matches("\\d*[sStTmM]?"));
        delayField.setResponder(value -> {
            while (commandDelays.size() <= index) {
                commandDelays.add(0);
            }
            commandDelays.set(index, parseDelayTicks(value));
        });
        delayFields.add(delayField);
        addRenderableWidget(delayField);

        int commandLeft = left + delayFieldWidth + gap;
        int commandWidth = commandFieldWidth - (delayFieldWidth + gap);

        EditBox commandField = new EditBox(font, commandLeft, y, commandWidth, 20,
                Component.translatable("screen.guardian_mod.trigger_area.console_command"));
        commandField.setMaxLength(32767);
        commandField.setValue(commandValues.get(index));
        commandField.setResponder(value -> {
            setActiveCommandField(commandField);
            updateCommandSuggestions(commandField);
        });
        commandFields.add(commandField);
        addRenderableWidget(commandField);

        CommandSuggestions suggestions = new CommandSuggestions(minecraft, this, commandField, font,
                true, false, 0, 7, false, 0x80000000);
        suggestions.setAllowSuggestions(true);
        suggestions.updateCommandInfo();
        commandSuggestions.add(suggestions);

        // Remove button right next to the command field
        int removeX = left + commandFieldWidth + buttonGap;
        if (index > 0) {
            addRenderableWidget(Button.builder(Component.literal("\u2212"), button -> removeCommandField(index))
                    .bounds(removeX, y, removeButtonWidth, 20)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(graphics);
        Component heading = Component.translatable("screen.guardian_mod.trigger_area.title");
        graphics.drawString(font, heading, width / 2 - font.width(heading) / 2, 10, 0xFFFFFF);

        if (TriggerArea.TYPE_COMMANDS.equals(triggerType)) {
            if (!delayFields.isEmpty()) {
                graphics.drawString(font, Component.translatable("screen.guardian_mod.trigger_area.delay"),
                        delayFields.get(0).getX(), delayFields.get(0).getY() - 11, 0xA0A0A0);
            }
            if (!commandFields.isEmpty()) {
                graphics.drawString(font, Component.translatable("screen.guardian_mod.trigger_area.console_command"),
                        commandFields.get(0).getX(), commandFields.get(0).getY() - 11, 0xA0A0A0);
            }
            if (triggerSelectorField != null && triggerSelectorField.isVisible()) {
                graphics.drawString(font, Component.translatable("screen.guardian_mod.trigger_area.trigger_selectors"),
                        triggerSelectorField.getX(), triggerSelectorField.getY() - 10, 0xA0A0A0);
            }
        } else if (privateSelectorField != null) {
            graphics.drawString(font, Component.translatable("screen.guardian_mod.trigger_area.private_selectors"),
                    privateSelectorField.getX(), privateSelectorField.getY() - 10, 0xA0A0A0);
        }

        super.render(graphics, mouseX, mouseY, delta);
        updateActiveCommandFieldFromFocus();
        CommandSuggestions suggestions = activeCommandSuggestions();
        if (suggestions != null) {
            suggestions.updateCommandInfo();
            suggestions.render(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        updateActiveCommandFieldFromFocus();
        CommandSuggestions suggestions = activeCommandSuggestions();
        if (suggestions != null && suggestions.keyPressed(event)) {
            return true;
        }
        boolean handled = super.keyPressed(event);
        if (handled) {
            updateActiveCommandFieldFromFocus();
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
        updateActiveCommandFieldFromMouse(event.x(), event.y());
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
        List<String> commands = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        for (int i = 0; i < commandValues.size(); i++) {
            String val = commandValues.get(i).trim();
            if (!val.isEmpty()) {
                commands.add(val);
                int d = 0;
                if (i < commandDelays.size()) {
                    d = commandDelays.get(i);
                }
                delays.add(d);
            }
        }
        if (triggerSelectorField != null) {
            triggerSelectors = triggerSelectorField.getValue();
        }
        if (privateSelectorField != null) {
            privateSelectors = privateSelectorField.getValue();
        }
        TriggerArea edited = area.edited(commands, delays, runOnce, triggerType, triggerMode, triggerSelectors,
                privateSelectors, restrictBlockBreaking, restrictAttacking, restrictInteractions);
        ClientPlayNetworking.send(new TriggerAreaPayloads.SaveEditor(edited.serialize()));
        onClose();
    }

    private void delete() {
        ClientPlayNetworking.send(new TriggerAreaPayloads.Delete(area.id));
        onClose();
    }

    private void reset() {
        area.runCount = 0;
        ClientPlayNetworking.send(new TriggerAreaPayloads.Reset(area.id));
    }

    private void addCommandField() {
        saveCommandValues();
        commandValues.add("");
        commandDelays.add(0);
        rebuildWidgets();
    }

    private void removeCommandField(int index) {
        saveCommandValues();
        if (index > 0 && index < commandValues.size()) {
            commandValues.remove(index);
            if (index < commandDelays.size()) {
                commandDelays.remove(index);
            }
            rebuildWidgets();
        }
    }

    private void saveCommandValues() {
        if (!TriggerArea.TYPE_COMMANDS.equals(triggerType) || commandFields.isEmpty()) {
            return;
        }
        commandValues.clear();
        for (int i = 0; i < commandFields.size(); i++) {
            commandValues.add(commandFields.get(i).getValue());
            if (i < delayFields.size()) {
                while (commandDelays.size() <= i) {
                    commandDelays.add(0);
                }
                commandDelays.set(i, parseDelayTicks(delayFields.get(i).getValue()));
            }
        }
    }

    private static int parseDelayTicks(String value) {
        String trimmed = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.isEmpty()) {
            return 0;
        }
        int multiplier = 1;
        char last = trimmed.charAt(trimmed.length() - 1);
        if (last == 's' || last == 't' || last == 'm') {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
            if (last == 's') {
                multiplier = 20;
            } else if (last == 'm') {
                multiplier = 1200;
            }
        }
        try {
            return Math.max(0, Integer.parseInt(trimmed) * multiplier);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void updateLabels() {
        typeButton.setMessage(Component.translatable("button.guardian_mod.trigger.type",
                Component.translatable(TriggerArea.TYPE_PRIVATE.equals(triggerType)
                        ? "button.guardian_mod.trigger.type.private"
                        : "button.guardian_mod.trigger.type.commands")));
        if (runButton != null) {
            runButton.setMessage(Component.translatable(runOnce ? "button.guardian_mod.trigger.run_once" : "button.guardian_mod.trigger.run_every_time"));
        }
        if (triggerModeButton != null) {
            triggerModeButton.setMessage(Component.translatable("button.guardian_mod.trigger.triggered_by",
                    Component.translatable("button.guardian_mod.trigger.mode." + ("everyone".equals(triggerMode) ? "everyone" : "selectors"))));
        }
        if (triggerSelectorField != null) {
            triggerSelectorField.setVisible(!"everyone".equals(triggerMode));
        }
    }

    private CommandSuggestions activeCommandSuggestions() {
        if (activeCommandIndex >= 0 && activeCommandIndex < commandSuggestions.size()) {
            return commandSuggestions.get(activeCommandIndex);
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
                activeCommandIndex = i;
                commandSuggestions.get(i).setAllowSuggestions(true);
                commandSuggestions.get(i).updateCommandInfo();
                return;
            }
        }
    }

    private void updateActiveCommandFieldFromMouse(double mouseX, double mouseY) {
        for (int i = 0; i < commandFields.size(); i++) {
            EditBox field = commandFields.get(i);
            if (mouseX >= field.getX() && mouseX <= field.getX() + field.getWidth()
                    && mouseY >= field.getY() && mouseY <= field.getY() + field.getHeight()) {
                setActiveCommandIndex(i);
                return;
            }
        }
        updateActiveCommandFieldFromFocus();
    }

    private void updateActiveCommandFieldFromFocus() {
        Object focused = getFocused();
        for (int i = 0; i < commandFields.size(); i++) {
            if (focused == commandFields.get(i)) {
                setActiveCommandIndex(i);
                return;
            }
        }
        activeCommandIndex = -1;
    }

    private void setActiveCommandField(EditBox field) {
        for (int i = 0; i < commandFields.size(); i++) {
            if (commandFields.get(i) == field) {
                setActiveCommandIndex(i);
                return;
            }
        }
    }

    private void setActiveCommandIndex(int index) {
        activeCommandIndex = index;
        for (int i = 0; i < commandFields.size(); i++) {
            commandFields.get(i).setFocused(i == index);
            if (i != index && i < commandSuggestions.size()) {
                commandSuggestions.get(i).hide();
            }
        }
        if (index >= 0 && index < commandFields.size()) {
            setFocused(commandFields.get(index));
            commandSuggestions.get(index).setAllowSuggestions(true);
        }
    }
}
