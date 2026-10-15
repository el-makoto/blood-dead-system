package net.anatomyblood.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

/**
 * Dedicated sub-part entity representing an individual limb/segment of the corpse
 * (Head, Torso, Right Arm, Left Arm, Right Leg, Left Leg).
 * Provides independent 3D hitboxes, raycast targeting, and player interaction.
 */
public class CorpsePart extends Entity {
    public enum PartType {
        HEAD(0.50f, 0.50f),
        TORSO(0.50f, 0.75f),
        RIGHT_ARM(0.25f, 0.75f),
        LEFT_ARM(0.25f, 0.75f),
        RIGHT_LEG(0.25f, 0.75f),
        LEFT_LEG(0.25f, 0.75f);

        public final float width;
        public final float height;

        PartType(float width, float height) {
            this.width = width;
            this.height = height;
        }
    }

    public final CorpseEntity owner;
    public final String name;
    public final PartType partType;
    private final EntityDimensions partDimensions;

    public CorpsePart(CorpseEntity owner, String name, PartType partType) {
        super(owner.getType(), owner.getWorld());
        this.owner = owner;
        this.name = name;
        this.partType = partType;
        this.partDimensions = EntityDimensions.changing(partType.width, partType.height);
        this.calculateDimensions();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public boolean canHit() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        return this.owner.interactWithPart(this, player, hand);
    }

    @Override
    public ActionResult interactAt(PlayerEntity player, net.minecraft.util.math.Vec3d hitPos, Hand hand) {
        return this.owner.interactWithPart(this, player, hand);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return this.owner.damage(world, source, amount);
    }

    @Override
    public boolean isPartOf(Entity other) {
        return this == other || this.owner == other;
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
        throw new UnsupportedOperationException("CorpsePart entities are managed by their parent CorpseEntity.");
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return this.partDimensions;
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public ItemStack getPickBlockStack() {
        return this.owner.getPickBlockStack();
    }

    public void updatePartBox(Box box) {
        this.lastRenderX = this.getX();
        this.lastRenderY = this.getY();
        this.lastRenderZ = this.getZ();
        this.prevX = this.getX();
        this.prevY = this.getY();
        this.prevZ = this.getZ();

        this.setBoundingBox(box);
        this.setPosition((box.minX + box.maxX) * 0.5D, box.minY, (box.minZ + box.maxZ) * 0.5D);
    }
}
