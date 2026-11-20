package fr.vengelis.industrialfacilities.frequency;

import java.util.UUID;
import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.alarm.AlarmBlockEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.panel.PanelBlockEntity;
import fr.vengelis.industrialfacilities.panel.PanelStructure;
import fr.vengelis.industrialfacilities.security.*;
import fr.vengelis.industrialfacilities.supervision.SupervisionConsoleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public record FrequencyEndpoint(UUID id, Type type, BlockPos pos, UUID owner) {
    public static final int TERMINAL_SLOTS = 4, CONSOLE_SLOTS = 8;
    public enum Type {
        DOOR, READER, KEYPASS, KEYPASS_MANAGER, BUTTON, LOCKDOWN, CAMERA_TERMINAL,
        CAMERA, ALARM, EMITTER, PANEL, CONSOLE, DISPLAY;

        public boolean reader() { return this == READER || this == KEYPASS; }
        public boolean terminal() {
            return reader() || this == KEYPASS_MANAGER || this == BUTTON || this == LOCKDOWN || this == CAMERA_TERMINAL;
        }
        public String cameraKind() {
            return switch (this) {
                case DOOR -> "door";
                case CAMERA -> "camera";
                case ALARM -> "alarm";
                case EMITTER -> "emitter";
                case PANEL -> "panel";
                default -> "";
            };
        }
    }

    public int slots() {
        return type == Type.CONSOLE ? CONSOLE_SLOTS : type.terminal() ? TERMINAL_SLOTS : 1;
    }

    public static FrequencyEndpoint resolve(ServerLevel level, BlockPos pos) {
        if (!TerminalLinks.loaded(level, pos)) return null;
        var block = level.getBlockEntity(pos);
        if (block instanceof AccessTerminalEntity) {
            var terminal = AccessTerminalEntity.resolve(level, pos);
            if (terminal == null || terminal.bank) return null;
            Type type = switch (terminal) {
                case CameraTerminalEntity camera -> Type.CAMERA_TERMINAL;
                case ControlTerminalEntity control -> control.lockdown() ? Type.LOCKDOWN : Type.BUTTON;
                case KeypassTerminalEntity keypass -> keypass.manager() ? Type.KEYPASS_MANAGER : Type.KEYPASS;
                default -> terminal.encoder ? null : Type.READER;
            };
            return type == null ? null : new FrequencyEndpoint(terminal.instanceId, type, terminal.getBlockPos(), terminal.creator);
        }
        if (block instanceof IndustrialDoorEntity) {
            var door = AccessDoorService.resolveDoor(level, pos);
            if (door == null || door.doorInstanceId == null) return null;
            return new FrequencyEndpoint(door.doorInstanceId, Type.DOOR, door.getBlockPos(), door.placedBy);
        }
        if (block instanceof SupervisionConsoleEntity) {
            var console = SupervisionConsoleEntity.resolve(level, pos);
            return console == null ? null : new FrequencyEndpoint(console.frequencyId(),
                console.isDisplay() ? Type.DISPLAY : Type.CONSOLE, console.getBlockPos(), console.frequencyOwner());
        }
        if (block instanceof SecurityCameraEntity camera)
            return new FrequencyEndpoint(camera.instanceId, Type.CAMERA, pos, camera.creator);
        if (block instanceof SignalEmitterEntity emitter)
            return new FrequencyEndpoint(emitter.instanceId, Type.EMITTER, pos, emitter.creator);
        if (block instanceof AlarmBlockEntity alarm)
            return new FrequencyEndpoint(alarm.automationInstanceId, Type.ALARM, pos, alarm.automationOwner);
        if (block instanceof PanelBlockEntity) {
            var group = PanelStructure.find(level, pos);
            var panel = group.valid() ? group.controller(level) : null;
            if (panel != null) return new FrequencyEndpoint(panel.automationInstanceId, Type.PANEL,
                panel.getBlockPos(), panel.automationOwner);
        }
        return null;
    }

    public boolean online(ServerLevel level) { return equals(resolve(level, pos)); }
}

