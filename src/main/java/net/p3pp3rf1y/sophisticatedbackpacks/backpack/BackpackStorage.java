package net.p3pp3rf1y.sophisticatedbackpacks.backpack;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.p3pp3rf1y.sophisticatedbackpacks.SophisticatedBackpacks;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BackpackStorage extends WorldSavedData {
	private static final String SAVED_DATA_NAME = SophisticatedBackpacks.MOD_ID;

	private final Map<UUID, CompoundNBT> backpackContents = new HashMap<>();
	private static final BackpackStorage clientStorageCopy = new BackpackStorage();
	private final Map<UUID, AccessLogRecord> accessLogRecords = new HashMap<>();

	private BackpackStorage() {
		super(SAVED_DATA_NAME);
	}

	public static BackpackStorage get() {
		if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER) {
			MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
			if (server != null) {
				ServerWorld overworld = server.getWorld(World.OVERWORLD);
				//noinspection ConstantConditions - by this time overworld is loaded
				DimensionSavedDataManager storage = overworld.getSavedData();
				return storage.getOrCreate(BackpackStorage::new, SAVED_DATA_NAME);
			}
		}
		return clientStorageCopy;
	}

	@Override
	public void read(CompoundNBT nbt) {
		readBackpackContents(nbt);
		readAccessLogs(nbt);
	}

	private void readAccessLogs(CompoundNBT nbt) {
		for (INBT n : nbt.getList("accessLogRecords", Constants.NBT.TAG_COMPOUND)) {
			AccessLogRecord alr = AccessLogRecord.deserializeFromNBT((CompoundNBT) n);
			accessLogRecords.put(alr.getBackpackUuid(), alr);
		}
	}

	private void readBackpackContents(CompoundNBT nbt) {
		for (INBT n : nbt.getList("backpackContents", Constants.NBT.TAG_COMPOUND)) {
			CompoundNBT uuidContentsPair = (CompoundNBT) n;
			UUID uuid = NBTUtil.readUniqueId(Objects.requireNonNull(uuidContentsPair.get("uuid")));
			CompoundNBT contents = uuidContentsPair.getCompound("contents");
			backpackContents.put(uuid, contents);
		}
	}

	@Override
	public CompoundNBT write(CompoundNBT compound) {
		CompoundNBT ret = new CompoundNBT();
		writeBackpackContents(ret);
		writeAccessLogs(ret);
		return ret;
	}

	private void writeBackpackContents(CompoundNBT ret) {
		ListNBT backpackContentsNbt = new ListNBT();
		for (Map.Entry<UUID, CompoundNBT> entry : backpackContents.entrySet()) {
			CompoundNBT uuidContentsPair = new CompoundNBT();
			uuidContentsPair.put("uuid", NBTUtil.func_240626_a_(entry.getKey()));
			uuidContentsPair.put("contents", entry.getValue());
			backpackContentsNbt.add(uuidContentsPair);
		}
		ret.put("backpackContents", backpackContentsNbt);
	}

	private void writeAccessLogs(CompoundNBT ret) {
		ListNBT accessLogsNbt = new ListNBT();
		for (AccessLogRecord alr : accessLogRecords.values()) {
			accessLogsNbt.add(alr.serializeToNBT());
		}
		ret.put("accessLogRecords", accessLogsNbt);
	}

	public CompoundNBT getOrCreateBackpackContents(UUID backpackUuid) {
		return backpackContents.computeIfAbsent(backpackUuid, uuid -> {
			markDirty();
			return new CompoundNBT();
		});
	}

	public void putAccessLog(AccessLogRecord alr) {
		accessLogRecords.put(alr.getBackpackUuid(), alr);
		markDirty();
	}

	public void removeBackpackContents(UUID backpackUuid) {
		backpackContents.remove(backpackUuid);
	}

	public void setBackpackContents(UUID backpackUuid, CompoundNBT contents) {
		backpackContents.put(backpackUuid, contents);
	}

	public Map<UUID, AccessLogRecord> getAccessLogs() {
		return accessLogRecords;
	}

	public int removeNonPlayerBackpackContents(boolean onlyWithEmptyInventory) {
		AtomicInteger numberRemoved = new AtomicInteger(0);
		backpackContents.entrySet().removeIf(entry -> {
			if (!accessLogRecords.containsKey(entry.getKey()) && (!onlyWithEmptyInventory || !entry.getValue().contains("inventory"))) {
				numberRemoved.incrementAndGet();
				return true;
			}
			return false;
		});
		if (numberRemoved.get() > 0) {
			markDirty();
		}
		return numberRemoved.get();
	}
}
