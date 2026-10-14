package net.anatomyblood.entity;

import net.anatomyblood.AnatomyOfBlood;
import net.anatomyblood.particle.ModParticles;
import net.anatomyblood.util.BloodConstants;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

public class CorpseEntity extends Entity {
    private static final TrackedData<Optional<UUID>> PLAYER_UUID = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<String> PLAYER_NAME = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Optional<UUID>> DRAGGING_PLAYER_UUID = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    public CorpseEntity(EntityType<?> type, World world) {
        super(type, world);
        this.intersectionChecked = true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(PLAYER_UUID, Optional.empty());
        builder.add(PLAYER_NAME, "");
        builder.add(DRAGGING_PLAYER_UUID, Optional.empty());
    }

    public void setPlayerUuid(UUID uuid) {
        this.dataTracker.set(PLAYER_UUID, Optional.ofNullable(uuid));
    }

    public Optional<UUID> getPlayerUuid() {
        return this.dataTracker.get(PLAYER_UUID);
    }

    public void setPlayerName(String name) {
        this.dataTracker.set(PLAYER_NAME, name == null ? "" : name);
    }

    public String getPlayerName() {
        return this.dataTracker.get(PLAYER_NAME);
    }

    public void setDraggingPlayerUuid(UUID uuid) {
        this.dataTracker.set(DRAGGING_PLAYER_UUID, Optional.ofNullable(uuid));
    }

    public Optional<UUID> getDraggingPlayerUuid() {
        return this.dataTracker.get(DRAGGING_PLAYER_UUID);
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (hand == Hand.MAIN_HAND && player.getMainHandStack().isEmpty()) {
            if (!this.getWorld().isClient()) {
                Optional<UUID> current = getDraggingPlayerUuid();
                if (current.isPresent() && current.get().equals(player.getUuid())) {
                    setDraggingPlayerUuid(null);
                    player.sendMessage(Text.literal("§cSoltaste el cadáver."), true);
                } else if (current.isEmpty()) {
                    setDraggingPlayerUuid(player.getUuid());
                    player.sendMessage(Text.literal("§aArrastrando cadáver..."), true);
                }
            }
            return ActionResult.SUCCESS;
        }
        return super.interact(player, hand);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    private net.anatomyblood.physics.JoltRagdoll ragdoll;

    public net.anatomyblood.physics.JoltRagdoll getRagdoll() {
        if (ragdoll == null) {
            ragdoll = new net.anatomyblood.physics.JoltRagdoll(this.getVelocity(), this.getYaw());
        }
        return ragdoll;
    }

    @Override
    public void tick() {
        super.tick();

        // Step Jolt Physics ragdoll simulation
        getRagdoll().tick(this.isOnGround(), this.getVelocity());

        // Server-only physics logic to prevent client-side desync & void clipping
        if (!this.getWorld().isClient()) {
            Optional<UUID> draggingUuid = getDraggingPlayerUuid();
            if (draggingUuid.isPresent()) {
                ServerWorld serverWorld = (ServerWorld) this.getWorld();
                PlayerEntity draggingPlayer = serverWorld.getPlayerByUuid(draggingUuid.get());

                if (draggingPlayer == null || !draggingPlayer.isAlive() || draggingPlayer.squaredDistanceTo(this) > 36.0D) {
                    setDraggingPlayerUuid(null);
                } else {
                    // Pull corpse smoothly behind player
                    Vec3d look = draggingPlayer.getRotationVector();
                    Vec3d target = draggingPlayer.getPos().subtract(look.x * 1.2D, 0.0D, look.z * 1.2D);
                    Vec3d diff = target.subtract(this.getPos());

                    this.setVelocity(diff.x * 0.4D, Math.max(-0.2D, diff.y * 0.4D), diff.z * 0.4D);
                    this.move(MovementType.SELF, this.getVelocity());

                    // Dragging blood trail particle (#F0509A)
                    if (this.age % 4 == 0) {
                        boolean despawnBlood = AnatomyOfBlood.shouldDespawnBlood(serverWorld);
                        SimpleParticleType stainParticle = despawnBlood ? ModParticles.BLOOD_STAIN_TEMPORARY : ModParticles.BLOOD_STAIN_PERMANENT;

                        serverWorld.spawnParticles(stainParticle, getX(), getY() + 0.05D, getZ(), 2, 0.2D, 0.0D, 0.2D, 0.0D);
                        serverWorld.spawnParticles(ModParticles.BLOOD_DRIP, getX(), getY() + 0.2D, getZ(), 3, 0.1D, 0.1D, 0.1D, 0.05D);
                    }
                }
            } else if (this.isTouchingWater()) {
                // Water buoyancy and flow physics
                Vec3d vel = this.getVelocity();
                this.setVelocity(vel.x * 0.9D, Math.min(0.05D, vel.y + 0.02D), vel.z * 0.9D);
                this.move(MovementType.SELF, this.getVelocity());
            } else if (!this.hasNoGravity()) {
                Vec3d vel = this.getVelocity();
                double newY = this.isOnGround() ? 0.0D : Math.max(-0.98D, vel.y - 0.08D);

                double newX = this.isOnGround() ? vel.x * 0.8D : vel.x * 0.98D;
                double newZ = this.isOnGround() ? vel.z * 0.8D : vel.z * 0.98D;

                this.setVelocity(newX, newY, newZ);
                this.move(MovementType.SELF, this.getVelocity());

                if (this.isOnGround() || this.verticalCollision) {
                    this.setVelocity(this.getVelocity().x, 0.0D, this.getVelocity().z);
                }
            }
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
        return new EntitySpawnS2CPacket(this, entityTrackerEntry);
    }

    @Override
    public boolean shouldSave() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true;
    }

    @Override
    public boolean isCollidable() {
        return false; // Return false to prevent physics pushing forces from clipping corpse into solid blocks
    }

    @Override
    public void onRemoved() {
        String env = this.getWorld().isClient() ? "CLIENT" : "SERVER";
        BloodConstants.LOGGER.info("[{}] CorpseEntity for player '{}' ({}) was removed at pos {} from world. Reason: {}", env, getPlayerName(), getPlayerUuid().orElse(null), getPos(), getRemovalReason());
        super.onRemoved();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("PlayerUuid")) {
            setPlayerUuid(nbt.getUuid("PlayerUuid"));
        }
        if (nbt.contains("PlayerName", NbtElement.STRING_TYPE)) {
            setPlayerName(nbt.getString("PlayerName"));
        }
        if (nbt.containsUuid("DraggingPlayerUuid")) {
            setDraggingPlayerUuid(nbt.getUuid("DraggingPlayerUuid"));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        getPlayerUuid().ifPresent(uuid -> nbt.putUuid("PlayerUuid", uuid));
        nbt.putString("PlayerName", getPlayerName());
        getDraggingPlayerUuid().ifPresent(uuid -> nbt.putUuid("DraggingPlayerUuid", uuid));
    }
}
