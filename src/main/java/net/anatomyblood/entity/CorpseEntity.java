package net.anatomyblood.entity;

import net.anatomyblood.AnatomyOfBlood;
import net.anatomyblood.particle.ModParticles;
import net.anatomyblood.physics.JoltRagdoll;
import net.anatomyblood.util.BloodConstants;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public class CorpseEntity extends Entity {
    private static final TrackedData<Optional<UUID>> PLAYER_UUID = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<String> PLAYER_NAME = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Optional<UUID>> DRAGGING_PLAYER_UUID = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Integer> DRAGGED_PART = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // Dedicated limb sub-entities with independent 3D bounding boxes and interactions
    public final CorpsePart headPart;
    public final CorpsePart torsoPart;
    public final CorpsePart rightArmPart;
    public final CorpsePart leftArmPart;
    public final CorpsePart rightLegPart;
    public final CorpsePart leftLegPart;
    public final CorpsePart[] parts;

    private JoltRagdoll ragdoll;
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw, lerpPitch;
    private int lerpSteps = 0;

    // Track previous dragging trail deposit position
    private double lastTrailX = Double.NaN;
    private double lastTrailY = Double.NaN;
    private double lastTrailZ = Double.NaN;
    private long lastInteractionTime = 0L;

    private static final Map<World, Int2ObjectMap<CorpsePart>> PARTS_BY_WORLD = new WeakHashMap<>();

    public static CorpsePart getPartById(World world, int id) {
        synchronized (PARTS_BY_WORLD) {
            Int2ObjectMap<CorpsePart> map = PARTS_BY_WORLD.get(world);
            return map != null ? map.get(id) : null;
        }
    }

    public static void registerParts(CorpseEntity corpse) {
        World world = corpse.getWorld();
        synchronized (PARTS_BY_WORLD) {
            Int2ObjectMap<CorpsePart> map = PARTS_BY_WORLD.computeIfAbsent(world, k -> new Int2ObjectOpenHashMap<>());
            for (CorpsePart part : corpse.parts) {
                map.put(part.getId(), part);
            }
        }
    }

    public static void unregisterParts(CorpseEntity corpse) {
        World world = corpse.getWorld();
        synchronized (PARTS_BY_WORLD) {
            Int2ObjectMap<CorpsePart> map = PARTS_BY_WORLD.get(world);
            if (map != null) {
                for (CorpsePart part : corpse.parts) {
                    map.remove(part.getId());
                }
            }
        }
    }

    public void assignPartIds() {
        int baseId = this.getId();
        for (int i = 0; i < parts.length; i++) {
            parts[i].setId(baseId + 1 + i);
        }
    }

    @Override
    public void setId(int id) {
        unregisterParts(this);
        super.setId(id);
        assignPartIds();
        registerParts(this);
    }

    public CorpseEntity(EntityType<?> type, World world) {
        super(type, world);
        this.intersectionChecked = true;

        this.headPart = new CorpsePart(this, "head", CorpsePart.PartType.HEAD);
        this.torsoPart = new CorpsePart(this, "torso", CorpsePart.PartType.TORSO);
        this.rightArmPart = new CorpsePart(this, "right_arm", CorpsePart.PartType.RIGHT_ARM);
        this.leftArmPart = new CorpsePart(this, "left_arm", CorpsePart.PartType.LEFT_ARM);
        this.rightLegPart = new CorpsePart(this, "right_leg", CorpsePart.PartType.RIGHT_LEG);
        this.leftLegPart = new CorpsePart(this, "left_leg", CorpsePart.PartType.LEFT_LEG);
        this.parts = new CorpsePart[]{headPart, torsoPart, rightArmPart, leftArmPart, rightLegPart, leftLegPart};

        assignPartIds();
        registerParts(this);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(PLAYER_UUID, Optional.empty());
        builder.add(PLAYER_NAME, "");
        builder.add(DRAGGING_PLAYER_UUID, Optional.empty());
        builder.add(DRAGGED_PART, 0); // 0 = Torso default
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

    public void setDraggedPartOrdinal(int ordinal) {
        this.dataTracker.set(DRAGGED_PART, ordinal);
    }

    public int getDraggedPartOrdinal() {
        return this.dataTracker.get(DRAGGED_PART);
    }

    public CorpsePart[] getParts() {
        return this.parts;
    }


    @Override
    public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
        if (hand != Hand.MAIN_HAND || !player.getMainHandStack().isEmpty()) {
            return ActionResult.PASS;
        }
        Optional<UUID> current = getDraggingPlayerUuid();
        if (current.isPresent() && current.get().equals(player.getUuid())) {
            return interactWithPart(torsoPart, player, hand);
        }

        Vec3d eyePos = player.getEyePos();
        Vec3d lookDir = player.getRotationVec(1.0F).normalize();

        CorpsePart targetPart = findTargetedPart(player, eyePos, lookDir);
        return interactWithPart(targetPart, player, hand);
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        return handleInteraction(player, hand);
    }

    public ActionResult handleInteraction(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND || !player.getMainHandStack().isEmpty()) {
            return ActionResult.PASS;
        }

        // If the player is already dragging this corpse, clicking anywhere drops it
        Optional<UUID> current = getDraggingPlayerUuid();
        if (current.isPresent() && current.get().equals(player.getUuid())) {
            return interactWithPart(torsoPart, player, hand);
        }

        Vec3d eyePos = player.getEyePos();
        Vec3d lookDir = player.getRotationVec(1.0F).normalize();

        CorpsePart targetPart = findTargetedPart(player, eyePos, lookDir);
        return interactWithPart(targetPart, player, hand);
    }

    /**
     * Finds the CorpsePart whose limb center is closest to a world-space position.
     * Used for position-based targeting when the exact click point is known (interactAt).
     * Much simpler and more reliable than the multi-phase ray detection.
     */
    public CorpsePart findNearestPartToPos(Vec3d worldPos) {
        if (worldPos == null) return torsoPart;
        JoltRagdoll rag = getRagdoll();
        Vec3d rShoulder = rag.getRightShoulderPos();
        Vec3d lShoulder = rag.getLeftShoulderPos();
        Vec3d rHip = rag.getRightHipPos();
        Vec3d lHip = rag.getLeftHipPos();

        // Midpoint of each limb segment as the representative center
        Vec3d headC  = new Vec3d(rag.head.x, rag.head.y, rag.head.z);
        Vec3d torsoC = new Vec3d((rag.pelvis.x + rag.chest.x) * 0.5, (rag.pelvis.y + rag.chest.y) * 0.5, (rag.pelvis.z + rag.chest.z) * 0.5);
        Vec3d rArmC  = new Vec3d((rShoulder.x + rag.rightHand.x) * 0.5, (rShoulder.y + rag.rightHand.y) * 0.5, (rShoulder.z + rag.rightHand.z) * 0.5);
        Vec3d lArmC  = new Vec3d((lShoulder.x + rag.leftHand.x)  * 0.5, (lShoulder.y + rag.leftHand.y)  * 0.5, (lShoulder.z + rag.leftHand.z)  * 0.5);
        Vec3d rLegC  = new Vec3d((rHip.x + rag.rightFoot.x) * 0.5, (rHip.y + rag.rightFoot.y) * 0.5, (rHip.z + rag.rightFoot.z) * 0.5);
        Vec3d lLegC  = new Vec3d((lHip.x + rag.leftFoot.x)  * 0.5, (lHip.y + rag.leftFoot.y)  * 0.5, (lHip.z + rag.leftFoot.z)  * 0.5);

        CorpsePart[] ps = { headPart,  torsoPart, rightArmPart, leftArmPart, rightLegPart, leftLegPart };
        Vec3d[]      cs = { headC,     torsoC,    rArmC,        lArmC,       rLegC,        lLegC };

        double minSq = Double.MAX_VALUE;
        CorpsePart best = torsoPart;
        for (int i = 0; i < ps.length; i++) {
            double dSq = worldPos.squaredDistanceTo(cs[i]);
            if (dSq < minSq) { minSq = dSq; best = ps[i]; }
        }
        return best;
    }

    public Vec3d getPartCenter(CorpsePart part) {
        if (part == null) return this.getPos();
        JoltRagdoll rag = getRagdoll();
        return switch (part.partType) {
            case HEAD -> new Vec3d(rag.head.x, rag.head.y, rag.head.z);
            case TORSO -> new Vec3d((rag.pelvis.x + rag.chest.x) * 0.5, (rag.pelvis.y + rag.chest.y) * 0.5, (rag.pelvis.z + rag.chest.z) * 0.5);
            case RIGHT_ARM -> {
                Vec3d s = rag.getRightShoulderPos();
                yield new Vec3d((s.x + rag.rightHand.x) * 0.5, (s.y + rag.rightHand.y) * 0.5, (s.z + rag.rightHand.z) * 0.5);
            }
            case LEFT_ARM -> {
                Vec3d s = rag.getLeftShoulderPos();
                yield new Vec3d((s.x + rag.leftHand.x) * 0.5, (s.y + rag.leftHand.y) * 0.5, (s.z + rag.leftHand.z) * 0.5);
            }
            case RIGHT_LEG -> {
                Vec3d h = rag.getRightHipPos();
                yield new Vec3d((h.x + rag.rightFoot.x) * 0.5, (h.y + rag.rightFoot.y) * 0.5, (h.z + rag.rightFoot.z) * 0.5);
            }
            case LEFT_LEG -> {
                Vec3d h = rag.getLeftHipPos();
                yield new Vec3d((h.x + rag.leftFoot.x) * 0.5, (h.y + rag.leftFoot.y) * 0.5, (h.z + rag.leftFoot.z) * 0.5);
            }
        };
    }

    public CorpsePart findTargetedPart(PlayerEntity player, Vec3d rayOrigin, Vec3d rayDir) {
        double reachDistance = player.getEntityInteractionRange() > 0 ? player.getEntityInteractionRange() : 5.0D;

        JoltRagdoll rag = getRagdoll();
        Vec3d p = rag.getInterpolatedPelvis(1.0F);
        Vec3d c = rag.getInterpolatedChest(1.0F);
        Vec3d h = rag.getInterpolatedHead(1.0F);
        Vec3d rh = rag.getInterpolatedRightHand(1.0F);
        Vec3d lh = rag.getInterpolatedLeftHand(1.0F);
        Vec3d rf = rag.getInterpolatedRightFoot(1.0F);
        Vec3d lf = rag.getInterpolatedLeftFoot(1.0F);
        Vec3d rs = rag.getInterpolatedRightShoulder(1.0F);
        Vec3d ls = rag.getInterpolatedLeftShoulder(1.0F);
        Vec3d rhip = rag.getInterpolatedRightHip(1.0F);
        Vec3d lhip = rag.getInterpolatedLeftHip(1.0F);

        // 1. Head (full 3D capsule from neck to crown of skull + sphere at center)
        Vec3d neckVec = h.subtract(c);
        double neckLen = neckVec.length();
        Vec3d headUp = neckLen > 0.001D ? neckVec.multiply(1.0D / neckLen) : new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d hNeck = c.add(headUp.multiply(0.10D));
        Vec3d hTop  = h.add(headUp.multiply(0.28D));
        double distSqHead = getDistanceSqRayToSegment(rayOrigin, rayDir, hNeck, hTop, reachDistance);

        Vec3d wHead = h.subtract(rayOrigin);
        double tHead = wHead.dotProduct(rayDir);
        if (tHead > 0.1D && tHead <= reachDistance + 0.5D) {
            double dPt = rayOrigin.add(rayDir.multiply(tHead)).squaredDistanceTo(h);
            if (dPt < distSqHead) distSqHead = dPt;
        }

        // 2. Torso (pelvis to mid-upper chest, stopping before neck/shoulders to prevent overlap)
        Vec3d torsoEnd = p.add(c.subtract(p).multiply(0.75D));
        double distSqTorso = getDistanceSqRayToSegment(rayOrigin, rayDir, p, torsoEnd, reachDistance);

        // 3. Limbs (3D shortest ray-to-segment distance)
        double distSqRArm  = getDistanceSqRayToSegment(rayOrigin, rayDir, rs, rh, reachDistance);
        double distSqLArm  = getDistanceSqRayToSegment(rayOrigin, rayDir, ls, lh, reachDistance);
        double distSqRLeg  = getDistanceSqRayToSegment(rayOrigin, rayDir, rhip, rf, reachDistance);
        double distSqLLeg  = getDistanceSqRayToSegment(rayOrigin, rayDir, lhip, lf, reachDistance);

        // Radii for normalization: generous radii for head and limbs, strict radius for torso.
        // This ensures crosshair aiming at head or limbs will never accidentally select the torso.
        double headRadius  = 0.42D;
        double armRadius   = 0.32D;
        double legRadius   = 0.32D;
        double torsoRadius = 0.18D;

        double normHead  = distSqHead  / (headRadius * headRadius);
        double normRArm  = distSqRArm  / (armRadius * armRadius);
        double normLArm  = distSqLArm  / (armRadius * armRadius);
        double normRLeg  = distSqRLeg  / (legRadius * legRadius);
        double normLLeg  = distSqLLeg  / (legRadius * legRadius);
        double normTorso = distSqTorso / (torsoRadius * torsoRadius);

        CorpsePart[] partsList = { headPart, rightArmPart, leftArmPart, rightLegPart, leftLegPart, torsoPart };
        double[] norms = { normHead, normRArm, normLArm, normRLeg, normLLeg, normTorso };

        CorpsePart bestPart = torsoPart;
        double minNorm = Double.MAX_VALUE;
        for (int i = 0; i < partsList.length; i++) {
            if (norms[i] < minNorm) {
                minNorm = norms[i];
                bestPart = partsList[i];
            }
        }

        return bestPart;
    }

    public CorpsePart findTargetedPart(PlayerEntity player) {
        return findTargetedPart(player, player.getEyePos(), player.getRotationVec(1.0F).normalize());
    }

    private static double getDistanceSqRayToSegment(Vec3d rayOrigin, Vec3d rayDir, Vec3d segA, Vec3d segB, double maxReach) {
        Vec3d u = rayDir;
        Vec3d v = segB.subtract(segA);
        Vec3d w = rayOrigin.subtract(segA);

        double a = u.dotProduct(u);
        double b = u.dotProduct(v);
        double c = v.dotProduct(v);
        double d = u.dotProduct(w);
        double e = v.dotProduct(w);
        double D = a * c - b * b;

        double sc, sN, sD = D;
        double tc, tN, tD = D;

        if (D < 0.0001D) {
            sN = 0.0D;
            sD = 1.0D;
            tN = e;
            tD = c;
        } else {
            sN = (b * e - c * d);
            tN = (a * e - b * d);
            if (sN < 0.0D) {
                sN = 0.0D;
                tN = e;
                tD = c;
            }
        }

        if (tN <= 0.0D) {
            tc = 0.0D;
            if (-d < 0.0D) sc = 0.0D;
            else sc = -d;
        } else if (tN >= tD) {
            tc = 1.0D;
            if ((-d + b) < 0.0D) sc = 0.0D;
            else sc = (-d + b);
        } else {
            tc = tN / tD;
            sc = sN / sD;
        }

        if (sc <= 0.1D || sc > maxReach + 0.5D) return Double.MAX_VALUE;

        Vec3d ptOnRay = rayOrigin.add(u.multiply(sc));
        Vec3d ptOnSeg = segA.add(v.multiply(tc));
        return ptOnRay.squaredDistanceTo(ptOnSeg);
    }

    public ActionResult interactWithPart(CorpsePart part, PlayerEntity player, Hand hand) {
        if (part == null) {
            part = torsoPart;
        }
        if (hand == Hand.MAIN_HAND && player.getMainHandStack().isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastInteractionTime < 250L) {
                return ActionResult.SUCCESS;
            }
            lastInteractionTime = now;

            if (!this.getWorld().isClient()) {
                Optional<UUID> current = getDraggingPlayerUuid();
                if (current.isPresent() && current.get().equals(player.getUuid())) {
                    setDraggingPlayerUuid(null);
                    player.sendMessage(Text.literal("§cSoltaste el cadáver."), true);
                } else if (current.isEmpty()) {
                    setDraggingPlayerUuid(player.getUuid());
                    setDraggedPartOrdinal(part.partType.ordinal());

                    String partName = switch (part.partType) {
                        case HEAD -> "la cabeza";
                        case TORSO -> "el torso";
                        case RIGHT_ARM -> "el brazo derecho";
                        case LEFT_ARM -> "el brazo izquierdo";
                        case RIGHT_LEG -> "la pierna derecha";
                        case LEFT_LEG -> "la pierna izquierda";
                    };
                    player.sendMessage(Text.literal("§aArrastrando cadáver desde " + partName + "..."), true);
                }
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    public JoltRagdoll getRagdoll() {
        if (ragdoll == null) {
            ragdoll = new JoltRagdoll(
                    this.getPlayerUuid().orElse(Util.NIL_UUID),
                    this.getVelocity(),
                    this.getYaw()
            );
        }
        return ragdoll;
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpPitch = pitch;
        this.lerpSteps = interpolationSteps;
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        unregisterParts(this);
        super.onSpawnPacket(packet);
        assignPartIds();
        registerParts(this);
    }

    @Override
    public void tick() {
        super.tick();

        // Client position network interpolation
        if (this.getWorld().isClient() && this.lerpSteps > 0) {
            double newX = this.getX() + (this.lerpX - this.getX()) / (double) this.lerpSteps;
            double newY = this.getY() + (this.lerpY - this.getY()) / (double) this.lerpSteps;
            double newZ = this.getZ() + (this.lerpZ - this.getZ()) / (double) this.lerpSteps;
            float newYaw = (float) (this.getYaw() + MathHelper.wrapDegrees(this.lerpYaw - this.getYaw()) / (double) this.lerpSteps);
            float newPitch = (float) (this.getPitch() + (this.lerpPitch - this.getPitch()) / (double) this.lerpSteps);
            this.lerpSteps--;
            this.setPosition(newX, newY, newZ);
            this.setRotation(newYaw, newPitch);
        }

        Optional<UUID> draggingUuid = getDraggingPlayerUuid();
        PlayerEntity draggingPlayer = null;

        if (draggingUuid.isPresent()) {
            if (this.getWorld().isClient()) {
                draggingPlayer = this.getWorld().getPlayerByUuid(draggingUuid.get());
            } else {
                ServerWorld serverWorld = (ServerWorld) this.getWorld();
                draggingPlayer = serverWorld.getPlayerByUuid(draggingUuid.get());
                if (draggingPlayer == null || !draggingPlayer.isAlive() || draggingPlayer.squaredDistanceTo(this) > 36.0D) {
                    setDraggingPlayerUuid(null);
                    draggingPlayer = null;
                }
            }
        }

        // Run authentic Verlet Ragdoll simulation on both client & server
        JoltRagdoll rag = getRagdoll();
        rag.tick(this.getWorld(), this, draggingPlayer, getDraggedPartOrdinal());

        // Update independent bounding boxes for all 6 limbs with comfortable hit margins
        headPart.updatePartBox(rag.getHeadBox().expand(0.08D, 0.15D, 0.08D));
        torsoPart.updatePartBox(rag.getTorsoBox().expand(0.06D, 0.12D, 0.06D));
        rightArmPart.updatePartBox(rag.getRightArmBox().expand(0.08D, 0.15D, 0.08D));
        leftArmPart.updatePartBox(rag.getLeftArmBox().expand(0.08D, 0.15D, 0.08D));
        rightLegPart.updatePartBox(rag.getRightLegBox().expand(0.08D, 0.15D, 0.08D));
        leftLegPart.updatePartBox(rag.getLeftLegBox().expand(0.08D, 0.15D, 0.08D));

        // Synchronize main entity position & bounding box to cover all limbs.
        this.setPos(rag.pelvis.x, rag.pelvis.y, rag.pelvis.z);
        this.setBoundingBox(rag.getFullBoundingBox().expand(0.15D, 0.25D, 0.15D));
        this.setYaw(rag.bodyYaw);
        this.setPitch(rag.bodyPitch);

        // Server-side: Dragging blood trail (#F0509A) - spawn ONLY when body physically drags on ground
        if (!this.getWorld().isClient()) {
            if (draggingPlayer != null) {
                // Find the ground-contacting node of the ragdoll
                boolean onGround = rag.isAnyPartOnGround(this.getWorld());
                JoltRagdoll.Node contactNode = rag.getGroundContactNode();
                Double surfaceY = onGround ? findGroundSurfaceY(this.getWorld(), contactNode.x, contactNode.y, contactNode.z) : null;

                if (surfaceY != null) {
                    double spawnX = contactNode.x;
                    double spawnY = surfaceY;
                    double spawnZ = contactNode.z;

                    if (this.age % 2 == 0) {
                        ServerWorld serverWorld = (ServerWorld) this.getWorld();
                        boolean despawnBlood = AnatomyOfBlood.shouldDespawnBlood(serverWorld);
                        SimpleParticleType trailParticle = despawnBlood ? ModParticles.BLOOD_TRAIL_TEMPORARY : ModParticles.BLOOD_TRAIL_PERMANENT;

                        double moveDx = spawnX - (Double.isNaN(lastTrailX) ? spawnX : lastTrailX);
                        double moveDz = spawnZ - (Double.isNaN(lastTrailZ) ? spawnZ : lastTrailZ);
                        double moveDist = Math.sqrt(moveDx * moveDx + moveDz * moveDz);

                        double dirX, dirZ;
                        if (moveDist > 0.005D) {
                            dirX = moveDx / moveDist;
                            dirZ = moveDz / moveDist;
                        } else {
                            Vec3d look = draggingPlayer.getRotationVec(1.0f);
                            double lookLen = Math.sqrt(look.x * look.x + look.z * look.z);
                            dirX = (lookLen > 0.001D) ? look.x / lookLen : 1.0D;
                            dirZ = (lookLen > 0.001D) ? look.z / lookLen : 0.0D;
                        }

                        // Spawn trail precisely at floor surface (never floating)
                        serverWorld.spawnParticles(trailParticle, spawnX, spawnY, spawnZ, 0, dirX, 0.0D, dirZ, 1.0D);

                        lastTrailX = spawnX;
                        lastTrailY = spawnY;
                        lastTrailZ = spawnZ;
                    }

                    if (this.age % 6 == 0) {
                        ServerWorld serverWorld = (ServerWorld) this.getWorld();
                        serverWorld.spawnParticles(ModParticles.BLOOD_DRIP, spawnX, spawnY + 0.04D, spawnZ, 1, 0.04D, 0.0D, 0.04D, 0.01D);
                    }
                } else {
                    // Suspended in air: do NOT spawn floor trail in air; only drip falling blood
                    lastTrailX = Double.NaN;
                    lastTrailY = Double.NaN;
                    lastTrailZ = Double.NaN;

                    if (this.age % 8 == 0) {
                        ServerWorld serverWorld = (ServerWorld) this.getWorld();
                        serverWorld.spawnParticles(ModParticles.BLOOD_DRIP, contactNode.x, contactNode.y, contactNode.z, 1, 0.04D, 0.0D, 0.04D, 0.01D);
                    }
                }
            } else {
                lastTrailX = Double.NaN;
                lastTrailY = Double.NaN;
                lastTrailZ = Double.NaN;
            }

            this.velocityModified = true;
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
        return false;
    }

    @Override
    public boolean canHit() {
        return true;
    }

    @Override
    public void onRemoved() {
        unregisterParts(this);
        String env = this.getWorld().isClient() ? "CLIENT" : "SERVER";
        BloodConstants.LOGGER.info("[{}] CorpseEntity for player '{}' ({}) was removed at pos {} from world. Reason: {}", env, getPlayerName(), getPlayerUuid().orElse(null), getPos(), getRemovalReason());
        super.onRemoved();
    }

    @Override
    public void remove(RemovalReason reason) {
        unregisterParts(this);
        super.remove(reason);
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
        if (nbt.contains("DraggedPart", NbtElement.INT_TYPE)) {
            setDraggedPartOrdinal(nbt.getInt("DraggedPart"));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        getPlayerUuid().ifPresent(uuid -> nbt.putUuid("PlayerUuid", uuid));
        nbt.putString("PlayerName", getPlayerName());
        getDraggingPlayerUuid().ifPresent(uuid -> nbt.putUuid("DraggingPlayerUuid", uuid));
        nbt.putInt("DraggedPart", getDraggedPartOrdinal());
    }

    /**
     * Finds the solid floor surface directly below the dragging contact point.
     * Returns null if the corpse is suspended in the air (prevents floating blood trails).
     */
    private Double findGroundSurfaceY(World world, double x, double y, double z) {
        BlockPos floored = BlockPos.ofFloored(x, y + 0.10D, z);
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos checkPos = floored.down(dy);
            BlockState state = world.getBlockState(checkPos);
            if (!state.isAir()) {
                VoxelShape colShape = state.getCollisionShape(world, checkPos);
                if (!colShape.isEmpty()) {
                    double top = checkPos.getY() + colShape.getMax(Direction.Axis.Y);
                    if (y >= top - 0.25D && y <= top + 0.40D) {
                        return top;
                    }
                }
            }
        }
        return null;
    }
}
