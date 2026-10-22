package net.anatomyblood.physics;

import net.anatomyblood.entity.CorpseEntity;
import net.anatomyblood.entity.CorpsePart;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Pure Verlet Ragdoll Physics Engine with Multipart World Block Collisions.
 * Simulates a realistic limp "muñeco de trapo" (floppy ragdoll):
 * - Independent 3D physical nodes for each body part.
 * - Real voxel block collisions for every limb.
 * - Dynamic dragging forces pulling on the grabbed limb.
 * - 60+ FPS sub-tick interpolation and authentic joint orientation.
 */
public class JoltRagdoll {

    public static class Node {
        public double x, y, z;
        public double prevX, prevY, prevZ;
        public double renderX, renderY, renderZ;
        public double prevRenderX, prevRenderY, prevRenderZ;
        public boolean onGround = false;

        public Node(double x, double y, double z) {
            set(x, y, z);
        }

        public void set(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.prevX = x;
            this.prevY = y;
            this.prevZ = z;
            this.renderX = x;
            this.renderY = y;
            this.renderZ = z;
            this.prevRenderX = x;
            this.prevRenderY = y;
            this.prevRenderZ = z;
            this.onGround = false;
        }

        public void savePrevious() {
            this.prevRenderX = this.renderX;
            this.prevRenderY = this.renderY;
            this.prevRenderZ = this.renderZ;
            this.renderX = this.x;
            this.renderY = this.y;
            this.renderZ = this.z;
        }

        public Vec3d getInterpolated(float tickDelta) {
            return new Vec3d(
                    MathHelper.lerp(tickDelta, prevRenderX, renderX),
                    MathHelper.lerp(tickDelta, prevRenderY, renderY),
                    MathHelper.lerp(tickDelta, prevRenderZ, renderZ)
            );
        }

        public void integrate(double friction, double gravity, double fx, double fy, double fz) {
            double vx = (x - prevX) * friction + fx;
            double vy = (y - prevY) * friction - gravity + fy;
            double vz = (z - prevZ) * friction + fz;

            prevX = x;
            prevY = y;
            prevZ = z;

            x += vx;
            y += vy;
            z += vz;
        }

        public void integrate(double friction, double gravity) {
            integrate(friction, gravity, 0.0D, 0.0D, 0.0D);
        }
    }

    // Physical nodes in world coordinates
    public final Node pelvis;
    public final Node chest;
    public final Node head;
    public final Node rightHand;
    public final Node leftHand;
    public final Node rightFoot;
    public final Node leftFoot;

    private boolean initialized = false;
    public boolean isInitialized() { return initialized; }
    private final UUID playerUuid;

    // Derived orientations
    public float bodyYaw = 0.0F;
    public float bodyPitch = 90.0F;
    public float bodyRoll = 0.0F;

    public float headPitch = 0.0F;
    public float headYaw = 0.0F;
    public float headRoll = 0.0F;

    public float rightArmPitch = 0.0F;
    public float rightArmYaw = 0.0F;
    public float rightArmRoll = 0.0F;

    public float leftArmPitch = 0.0F;
    public float leftArmYaw = 0.0F;
    public float leftArmRoll = 0.0F;

    public float rightLegPitch = 0.0F;
    public float rightLegYaw = 0.0F;
    public float rightLegRoll = 0.0F;

    public float leftLegPitch = 0.0F;
    public float leftLegYaw = 0.0F;
    public float leftLegRoll = 0.0F;

    // Previous tick values for sub-tick smooth interpolation (60+ FPS)
    public float prevBodyYaw = 0.0F;
    public float prevBodyPitch = 90.0F;
    public float prevBodyRoll = 0.0F;

    public float prevHeadPitch = 0.0F;
    public float prevHeadYaw = 0.0F;
    public float prevHeadRoll = 0.0F;

    public float prevRightArmPitch = 0.0F;
    public float prevRightArmYaw = 0.0F;
    public float prevRightArmRoll = 0.0F;

    public float prevLeftArmPitch = 0.0F;
    public float prevLeftArmYaw = 0.0F;
    public float prevLeftArmRoll = 0.0F;

    public float prevRightLegPitch = 0.0F;
    public float prevRightLegYaw = 0.0F;
    public float prevRightLegRoll = 0.0F;

    public float prevLeftLegPitch = 0.0F;
    public float prevLeftLegYaw = 0.0F;
    public float prevLeftLegRoll = 0.0F;

    public JoltRagdoll(UUID playerUuid, Vec3d initialVelocity, float initialYaw) {
        this.playerUuid = playerUuid;
        this.bodyYaw = initialYaw;
        this.prevBodyYaw = initialYaw;

        this.pelvis = new Node(0.0D, 0.0D, 0.0D);
        this.chest = new Node(0.0D, 0.0D, 0.55D);
        this.head = new Node(0.0D, 0.0D, 0.95D);
        this.rightHand = new Node(-0.35D, 0.0D, 0.40D);
        this.leftHand = new Node(0.35D, 0.0D, 0.40D);
        this.rightFoot = new Node(-0.18D, 0.0D, -0.75D);
        this.leftFoot = new Node(0.18D, 0.0D, -0.75D);
    }

    public JoltRagdoll(Vec3d initialVelocity, float initialYaw) {
        this(null, initialVelocity, initialYaw);
    }

    public void initPose(CorpseEntity corpse) {
        double px = corpse.getX();
        double py = corpse.getY();
        double pz = corpse.getZ();
        float yawRad = (float) Math.toRadians(corpse.getYaw());

        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = -forwardZ;
        double rightZ = forwardX;

        // Position nodes in world space flat on the ground
        pelvis.set(px, py + 0.12D, pz);
        chest.set(px + forwardX * 0.55D, py + 0.12D, pz + forwardZ * 0.55D);
        head.set(px + forwardX * 0.92D, py + 0.12D, pz + forwardZ * 0.92D);

        rightHand.set(px + forwardX * 0.40D - rightX * 0.35D, py + 0.10D, pz + forwardZ * 0.40D - rightZ * 0.35D);
        leftHand.set(px + forwardX * 0.40D + rightX * 0.35D, py + 0.10D, pz + forwardZ * 0.40D + rightZ * 0.35D);

        rightFoot.set(px - forwardX * 0.72D - rightX * 0.18D, py + 0.10D, pz - forwardZ * 0.72D - rightZ * 0.18D);
        leftFoot.set(px - forwardX * 0.72D + rightX * 0.18D, py + 0.10D, pz - forwardZ * 0.72D + rightZ * 0.18D);

        initialized = true;
    }

    /**
     * Ticks the ragdoll physics in world coordinates with true voxel block collisions.
     */
    public void tick(World world, CorpseEntity corpse, PlayerEntity draggingPlayer, int draggedPartOrdinal) {
        if (!initialized) {
            initPose(corpse);
        }

        savePreviousState();

        // 1. Verlet Integration with gravity, fluid dynamics, buoyancy, currents, and bubble columns
        integrateNode(world, corpse, pelvis);
        integrateNode(world, corpse, chest);
        integrateNode(world, corpse, head);
        integrateNode(world, corpse, rightHand);
        integrateNode(world, corpse, leftHand);
        integrateNode(world, corpse, rightFoot);
        integrateNode(world, corpse, leftFoot);

        // 2. Dragging Pull Forces
        if (draggingPlayer != null && draggingPlayer.isAlive()) {
            Vec3d look = draggingPlayer.getRotationVec(1.0F);
            // Target hold position in front of dragging player at waist/hand height
            Vec3d holdPos = draggingPlayer.getEyePos().add(look.x * 0.9D, -0.45D, look.z * 0.9D);

            CorpsePart.PartType draggedPart = (draggedPartOrdinal >= 0 && draggedPartOrdinal < CorpsePart.PartType.values().length)
                    ? CorpsePart.PartType.values()[draggedPartOrdinal]
                    : CorpsePart.PartType.TORSO;

            switch (draggedPart) {
                case HEAD -> pullTowards(head, holdPos, 0.55D);
                case RIGHT_ARM -> pullTowards(rightHand, holdPos, 0.55D);
                case LEFT_ARM -> pullTowards(leftHand, holdPos, 0.55D);
                case RIGHT_LEG -> pullTowards(rightFoot, holdPos, 0.55D);
                case LEFT_LEG -> pullTowards(leftFoot, holdPos, 0.55D);
                case TORSO -> {
                    pullTowards(chest, holdPos, 0.50D);
                    pullTowards(rightHand, holdPos.add(-0.2D, -0.1D, 0.0D), 0.35D);
                    pullTowards(leftHand, holdPos.add(0.2D, -0.1D, 0.0D), 0.35D);
                }
            }
        }

        // 3. Distance Constraints and World Block Collisions (10 iterations)
        for (int iter = 0; iter < 10; iter++) {
            solveDistanceConstraints();
            resolveWorldCollisions(world);
        }

        // 4. Derive joint angles and model orientations
        computeModelTransforms();
    }

    private void integrateNode(World world, CorpseEntity corpse, Node node) {
        BlockPos pos = BlockPos.ofFloored(node.x, node.y, node.z);
        FluidState fluidState = world.getFluidState(pos);
        boolean inWater = fluidState.isIn(FluidTags.WATER);

        if (!inWater) {
            BlockPos below = pos.down();
            FluidState belowFluid = world.getFluidState(below);
            if (belowFluid.isIn(FluidTags.WATER)) {
                double surface = below.getY() + belowFluid.getHeight(world, below);
                if (node.y < surface) {
                    pos = below;
                    fluidState = belowFluid;
                    inWater = true;
                }
            }
        }

        double fx = 0.0D;
        double fy = 0.0D;
        double fz = 0.0D;

        if (inWater) {
            float fluidHeight = fluidState.getHeight(world, pos);
            double waterSurfaceY = pos.getY() + fluidHeight;

            // Submergence factor: 0.0 (just at surface) to 1.0 (fully submerged)
            double submergence = MathHelper.clamp((waterSurfaceY - node.y) / 0.35D, 0.1D, 1.0D);

            // Water viscosity / drag (higher damping than air)
            double friction = MathHelper.lerp(submergence, 0.92D, 0.82D);

            // Buoyancy: corpse slowly and gently sinks in water (0.006D vs 0.045D in air)
            double gravity = MathHelper.lerp(submergence, 0.045D, 0.006D);

            // 1. Water Currents / Flow direction
            Vec3d flow = fluidState.getVelocity(world, pos);
            double flowLen = flow.length();
            if (flowLen > 0.001D) {
                double push = Math.min(flowLen, 0.25D) * 0.035D * submergence;
                fx += (flow.x / flowLen) * push;
                fy += (flow.y / flowLen) * push;
                fz += (flow.z / flowLen) * push;
            }

            // 2. Bubble Columns (Soul Sand & Magma Block)
            BlockState blockState = world.getBlockState(pos);
            if (!blockState.isOf(Blocks.BUBBLE_COLUMN)) {
                BlockState belowState = world.getBlockState(pos.down());
                if (belowState.isOf(Blocks.BUBBLE_COLUMN) && node.y <= pos.getY() + 0.35D) {
                    blockState = belowState;
                    pos = pos.down();
                }
            }

            if (blockState.isOf(Blocks.BUBBLE_COLUMN)) {
                boolean isDownward = blockState.contains(BubbleColumnBlock.DRAG) && blockState.get(BubbleColumnBlock.DRAG);
                int seed = corpse != null ? corpse.getId() : 0;
                int age = corpse != null ? corpse.age : 0;

                if (isDownward) {
                    // Magma Block: Whirlpool downward suction
                    double magmaPull = 0.055D;
                    fy -= magmaPull;

                    double swirl = (seed * 31 + age * 0.25D);
                    fx += Math.cos(swirl) * 0.008D;
                    fz += Math.sin(swirl) * 0.008D;
                } else {
                    // Soul Sand: Geyser upward push
                    double soulPush = 0.070D;
                    BlockState aboveState = world.getBlockState(pos.up());
                    boolean isAtSurface = aboveState.isAir();

                    if (isAtSurface) {
                        // Bob smoothly at surface of water
                        double surfaceDist = node.y - (pos.getY() + 0.75D);
                        if (surfaceDist < 0.0D) {
                            fy += soulPush * (1.0D + surfaceDist * 2.0D);
                        } else {
                            fy -= Math.min(surfaceDist * 0.04D, 0.03D);
                        }
                        double wobble = (seed * 17 + age * 0.35D);
                        fx += Math.sin(wobble) * 0.010D;
                        fz += Math.cos(wobble) * 0.010D;
                    } else {
                        // Rapid ascent through water column
                        fy += soulPush;
                        double turbulence = (seed * 13 + age * 0.25D);
                        fx += Math.sin(turbulence) * 0.005D;
                        fz += Math.cos(turbulence) * 0.005D;
                    }
                }
            }

            node.integrate(friction, gravity, fx, fy, fz);
        } else {
            // Standard air physics with joint tension compensation for hanging limbs
            double airGravity = 0.045D;
            if ((node == rightFoot || node == leftFoot) && node.y < pelvis.y - 0.60D) {
                airGravity = 0.008D;
            } else if ((node == rightHand || node == leftHand) && node.y < chest.y - 0.50D) {
                airGravity = 0.010D;
            }

            double speedSq = (node.x - node.prevX) * (node.x - node.prevX)
                    + (node.y - node.prevY) * (node.y - node.prevY)
                    + (node.z - node.prevZ) * (node.z - node.prevZ);

            // Micro-movement rest damping: settle hanging limbs and grounded bodies to rest
            double airFriction = speedSq < 0.0004D ? 0.70D : 0.88D;
            if (speedSq < 0.000025D && (node.onGround || airGravity < 0.02D)) {
                node.prevX = node.x;
                node.prevY = node.y;
                node.prevZ = node.z;
                return;
            }

            node.integrate(airFriction, airGravity, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * Computes a stable, mathematically continuous rightward unit vector for the torso.
     * Prevents gimbal lock, zero cross-products, and axis flipping in all orientations.
     */
    public static Vec3d computeTorsoRight(Vec3d spineVec, float bodyYaw) {
        double spineLen = spineVec.length();
        Vec3d uUp = spineLen > 0.001D ? spineVec.multiply(1.0D / spineLen) : new Vec3d(0.0D, 1.0D, 0.0D);
        if (Math.abs(uUp.y) < 0.92D) {
            // Horizontal ground normal cross product: always smooth, horizontal, and never flips
            Vec3d r = uUp.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D)).normalize();
            if (r.lengthSquared() > 0.001D) {
                return r;
            }
        }
        float yawRad = (float) Math.toRadians(bodyYaw);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        return new Vec3d(-fwdZ, 0.0D, fwdX).normalize();
    }

    private void pullTowards(Node node, Vec3d target, double strength) {
        node.x += (target.x - node.x) * strength;
        node.y += (target.y - node.y) * strength;
        node.z += (target.z - node.z) * strength;
    }

    private void solveDistanceConstraints() {
        // Spine: Pelvis <-> Chest = 0.55m
        solveDistance(pelvis, chest, 0.55D, 0.95D);
        // Neck: Chest <-> Head = 0.35m
        solveDistance(chest, head, 0.35D, 0.95D);

        // Derive torso frame, shoulders and hips
        Vec3d pelvisPos = new Vec3d(pelvis.x, pelvis.y, pelvis.z);
        Vec3d chestPos = new Vec3d(chest.x, chest.y, chest.z);
        Vec3d spine = chestPos.subtract(pelvisPos);
        double spineLen = spine.length();
        Vec3d uUp = spineLen > 0.001D ? spine.multiply(1.0D / spineLen) : new Vec3d(0.0D, 1.0D, 0.0D);

        Vec3d uRight = computeTorsoRight(spine, bodyYaw);
        Vec3d uFront = uUp.crossProduct(uRight).normalize();

        // Shoulders and hips in world space
        Vec3d rightShoulder = chestPos.add(uRight.multiply(0.24D)).add(uUp.multiply(0.04D));
        Vec3d leftShoulder = chestPos.subtract(uRight.multiply(0.24D)).add(uUp.multiply(0.04D));
        Vec3d rightHip = pelvisPos.add(uRight.multiply(0.14D));
        Vec3d leftHip = pelvisPos.subtract(uRight.multiply(0.14D));

        // Arms from shoulders (target 0.60m)
        solvePointDistance(rightHand, rightShoulder, chest, 0.60D, 0.90D);
        solvePointDistance(leftHand, leftShoulder, chest, 0.60D, 0.90D);

        // Legs from hips (target 0.72m)
        solvePointDistance(rightFoot, rightHip, pelvis, 0.72D, 0.90D);
        solvePointDistance(leftFoot, leftHip, pelvis, 0.72D, 0.90D);

        // 1. Neck angular flexion limit (head cannot bend > 45° from spine)
        Vec3d neck = new Vec3d(head.x - chest.x, head.y - chest.y, head.z - chest.z);
        double neckLen = neck.length();
        if (neckLen > 0.001D) {
            Vec3d neckDir = neck.multiply(1.0D / neckLen);
            double dot = neckDir.dotProduct(uUp);
            if (dot < 0.70D) {
                Vec3d corrected = neckDir.add(uUp.multiply(0.72D - dot)).normalize();
                head.x = chest.x + corrected.x * 0.35D;
                head.y = chest.y + corrected.y * 0.35D;
                head.z = chest.z + corrected.z * 0.35D;
            }
        }

        // 2. Head collisions: cannot collapse into shoulders or torso
        solvePointMinDistance(head, rightShoulder, 0.36D);
        solvePointMinDistance(head, leftShoulder, 0.36D);
        resolveTorsoPenetration(head, pelvisPos, chestPos, uRight, uFront, uUp, 0.22D);
        solveMinDistance(pelvis, head, 0.78D);

        // 3. Arm vs Torso collision (hands and elbows pushed out of torso volume)
        resolveTorsoPenetration(rightHand, pelvisPos, chestPos, uRight, uFront, uUp, 0.11D);
        resolveTorsoPenetration(leftHand, pelvisPos, chestPos, uRight, uFront, uUp, 0.11D);
        resolveMidpointTorsoPenetration(rightShoulder, rightHand, pelvisPos, chestPos, uRight, uFront, uUp, 0.11D);
        resolveMidpointTorsoPenetration(leftShoulder, leftHand, pelvisPos, chestPos, uRight, uFront, uUp, 0.11D);

        // 4. Arm vs Head collision (arms cannot clip through head)
        solveMinDistance(head, rightHand, 0.36D);
        solveMinDistance(head, leftHand, 0.36D);
        solvePointToMidpointMinDistance(head, rightShoulder, rightHand, 0.34D);
        solvePointToMidpointMinDistance(head, leftShoulder, leftHand, 0.34D);

        // 5. Arm vs Arm collision (hands and forearms push apart)
        solveMinDistance(rightHand, leftHand, 0.24D);
        solveMidpointToMidpointMinDistance(rightShoulder, rightHand, leftShoulder, leftHand, 0.22D);
        solvePointToMidpointMinDistance(rightHand, leftShoulder, leftHand, 0.22D);
        solvePointToMidpointMinDistance(leftHand, rightShoulder, rightHand, 0.22D);

        // 6. Leg vs Leg collision (feet and knees push apart gently without jitter)
        solveMinDistance(rightFoot, leftFoot, 0.22D);
        solveMidpointToMidpointMinDistance(rightHip, rightFoot, leftHip, leftFoot, 0.20D);
        solveMaxDistance(rightFoot, leftFoot, 0.75D);

        // 7. Leg vs Torso collision (only when limbs bend upwards towards chest, not while hanging down)
        if (rightFoot.y >= pelvisPos.y - 0.10D) {
            resolveTorsoPenetration(rightFoot, pelvisPos, chestPos, uRight, uFront, uUp, 0.12D);
            resolveMidpointTorsoPenetration(rightHip, rightFoot, pelvisPos, chestPos, uRight, uFront, uUp, 0.12D);
        }
        if (leftFoot.y >= pelvisPos.y - 0.10D) {
            resolveTorsoPenetration(leftFoot, pelvisPos, chestPos, uRight, uFront, uUp, 0.12D);
            resolveMidpointTorsoPenetration(leftHip, leftFoot, pelvisPos, chestPos, uRight, uFront, uUp, 0.12D);
        }

        // 8. Arm vs Leg collision
        solveMinDistance(rightHand, rightFoot, 0.22D);
        solveMinDistance(leftHand, leftFoot, 0.22D);
        solveMinDistance(rightHand, leftFoot, 0.22D);
        solveMinDistance(leftHand, rightFoot, 0.22D);
        solvePointToMidpointMinDistance(rightHand, rightHip, rightFoot, 0.22D);
        solvePointToMidpointMinDistance(leftHand, leftHip, leftFoot, 0.22D);
    }

    private void solveDistance(Node a, Node b, double targetDist, double stiffness) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.0001D) return;

        double diff = (dist - targetDist) / dist * stiffness * 0.5D;
        a.x += dx * diff;
        a.y += dy * diff;
        a.z += dz * diff;
        b.x -= dx * diff;
        b.y -= dy * diff;
        b.z -= dz * diff;
    }

    private void solvePointDistance(Node limbEnd, Vec3d jointPoint, Node jointRoot, double targetDist, double stiffness) {
        double dx = limbEnd.x - jointPoint.x;
        double dy = limbEnd.y - jointPoint.y;
        double dz = limbEnd.z - jointPoint.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.0001D) return;

        double diff = (dist - targetDist) / dist * stiffness;

        // If jointRoot (pelvis/chest) is resting on ground and limb is hanging below it,
        // do not pull the grounded joint root downwards into the block
        if (jointRoot.onGround && dy < 0.0D) {
            limbEnd.x -= dx * diff;
            limbEnd.y -= dy * diff;
            limbEnd.z -= dz * diff;
        } else {
            limbEnd.x -= dx * diff * 0.6D;
            limbEnd.y -= dy * diff * 0.6D;
            limbEnd.z -= dz * diff * 0.6D;

            jointRoot.x += dx * diff * 0.4D;
            jointRoot.y += dy * diff * 0.4D;
            jointRoot.z += dz * diff * 0.4D;
        }
    }

    private void solveMinDistance(Node a, Node b, double minDist) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.0001D || dist >= minDist) return;

        double diff = (dist - minDist) / dist * 0.5D;
        a.x += dx * diff;
        a.y += dy * diff;
        a.z += dz * diff;
        b.x -= dx * diff;
        b.y -= dy * diff;
        b.z -= dz * diff;
    }

    private void solveMaxDistance(Node a, Node b, double maxDist) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= maxDist) return;

        double diff = (dist - maxDist) / dist * 0.5D;
        a.x += dx * diff;
        a.y += dy * diff;
        a.z += dz * diff;
        b.x -= dx * diff;
        b.y -= dy * diff;
        b.z -= dz * diff;
    }

    private void solveSegmentMinDistance(Node p, Node a, Node b, double minDist) {
        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double abz = b.z - a.z;
        double apx = p.x - a.x;
        double apy = p.y - a.y;
        double apz = p.z - a.z;

        double abLenSq = abx * abx + aby * aby + abz * abz;
        if (abLenSq < 0.0001D) return;

        double t = MathHelper.clamp((apx * abx + apy * aby + apz * abz) / abLenSq, 0.0D, 1.0D);
        double closestX = a.x + t * abx;
        double closestY = a.y + t * aby;
        double closestZ = a.z + t * abz;

        double dx = p.x - closestX;
        double dy = p.y - closestY;
        double dz = p.z - closestZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist < minDist && dist > 0.0001D) {
            double push = (minDist - dist) / dist;
            p.x += dx * push;
            p.y += dy * push;
            p.z += dz * push;
        }
    }

    private void resolveTorsoPenetration(Node node, Vec3d pelvisPos, Vec3d chestPos, Vec3d uRight, Vec3d uFront, Vec3d uUp, double radius) {
        double px = node.x - pelvisPos.x;
        double py = node.y - pelvisPos.y;
        double pz = node.z - pelvisPos.z;

        double spineDist = px * uUp.x + py * uUp.y + pz * uUp.z;
        double torsoLen = chestPos.distanceTo(pelvisPos);

        if (spineDist < -0.05D || spineDist > torsoLen + 0.10D) {
            return;
        }

        double lateralDist = px * uRight.x + py * uRight.y + pz * uRight.z;
        double depthDist = px * uFront.x + py * uFront.y + pz * uFront.z;

        double halfWidth = 0.24D + radius;
        double halfDepth = 0.13D + radius;

        double overlapX = halfWidth - Math.abs(lateralDist);
        double overlapZ = halfDepth - Math.abs(depthDist);

        if (overlapX > 0.0D && overlapZ > 0.0D) {
            if (overlapX < overlapZ) {
                double sign = lateralDist >= 0 ? 1.0D : -1.0D;
                node.x += uRight.x * overlapX * sign;
                node.y += uRight.y * overlapX * sign;
                node.z += uRight.z * overlapX * sign;
            } else {
                double sign = depthDist >= 0 ? 1.0D : -1.0D;
                node.x += uFront.x * overlapZ * sign;
                node.y += uFront.y * overlapZ * sign;
                node.z += uFront.z * overlapZ * sign;
            }
        }
    }

    private void resolveMidpointTorsoPenetration(Vec3d anchor, Node endNode, Vec3d pelvisPos, Vec3d chestPos, Vec3d uRight, Vec3d uFront, Vec3d uUp, double radius) {
        double mx = (anchor.x + endNode.x) * 0.5D;
        double my = (anchor.y + endNode.y) * 0.5D;
        double mz = (anchor.z + endNode.z) * 0.5D;

        double px = mx - pelvisPos.x;
        double py = my - pelvisPos.y;
        double pz = mz - pelvisPos.z;

        double spineDist = px * uUp.x + py * uUp.y + pz * uUp.z;
        double torsoLen = chestPos.distanceTo(pelvisPos);

        if (spineDist < -0.05D || spineDist > torsoLen + 0.10D) {
            return;
        }

        double lateralDist = px * uRight.x + py * uRight.y + pz * uRight.z;
        double depthDist = px * uFront.x + py * uFront.y + pz * uFront.z;

        double halfWidth = 0.24D + radius;
        double halfDepth = 0.13D + radius;

        double overlapX = halfWidth - Math.abs(lateralDist);
        double overlapZ = halfDepth - Math.abs(depthDist);

        if (overlapX > 0.0D && overlapZ > 0.0D) {
            if (overlapX < overlapZ) {
                double sign = lateralDist >= 0 ? 1.0D : -1.0D;
                endNode.x += uRight.x * overlapX * sign * 0.5D;
                endNode.y += uRight.y * overlapX * sign * 0.5D;
                endNode.z += uRight.z * overlapX * sign * 0.5D;
            } else {
                double sign = depthDist >= 0 ? 1.0D : -1.0D;
                endNode.x += uFront.x * overlapZ * sign * 0.5D;
                endNode.y += uFront.y * overlapZ * sign * 0.5D;
                endNode.z += uFront.z * overlapZ * sign * 0.5D;
            }
        }
    }

    private void solvePointMinDistance(Node a, Vec3d b, double minDist) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.0001D || dist >= minDist) return;

        double diff = (dist - minDist) / dist;
        a.x += dx * diff;
        a.y += dy * diff;
        a.z += dz * diff;
    }

    private void solvePointToMidpointMinDistance(Node a, Vec3d anchor, Node endNode, double minDist) {
        double mx = (anchor.x + endNode.x) * 0.5D;
        double my = (anchor.y + endNode.y) * 0.5D;
        double mz = (anchor.z + endNode.z) * 0.5D;

        double dx = mx - a.x;
        double dy = my - a.y;
        double dz = mz - a.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.0001D || dist >= minDist) return;

        double diff = (dist - minDist) / dist * 0.5D;
        a.x += dx * diff;
        a.y += dy * diff;
        a.z += dz * diff;
        endNode.x -= dx * diff;
        endNode.y -= dy * diff;
        endNode.z -= dz * diff;
    }

    private void solveMidpointToMidpointMinDistance(Vec3d anchorA, Node endA, Vec3d anchorB, Node endB, double minDist) {
        double ax = (anchorA.x + endA.x) * 0.5D;
        double ay = (anchorA.y + endA.y) * 0.5D;
        double az = (anchorA.z + endA.z) * 0.5D;

        double bx = (anchorB.x + endB.x) * 0.5D;
        double by = (anchorB.y + endB.y) * 0.5D;
        double bz = (anchorB.z + endB.z) * 0.5D;

        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.0001D || dist >= minDist) return;

        double diff = (dist - minDist) / dist * 0.5D;
        endA.x += dx * diff;
        endA.y += dy * diff;
        endA.z += dz * diff;
        endB.x -= dx * diff;
        endB.y -= dy * diff;
        endB.z -= dz * diff;
    }

    private void resolveWorldCollisions(World world) {
        resolveNodeBlockCollision(world, pelvis, 0.16D);
        resolveNodeBlockCollision(world, chest, 0.18D);
        resolveNodeBlockCollision(world, head, 0.18D);
        resolveNodeBlockCollision(world, rightHand, 0.12D);
        resolveNodeBlockCollision(world, leftHand, 0.12D);
        resolveNodeBlockCollision(world, rightFoot, 0.14D);
        resolveNodeBlockCollision(world, leftFoot, 0.14D);
    }

    private void resolveNodeBlockCollision(World world, Node node, double radius) {
        node.onGround = false;
        Box box = new Box(node.x - radius, node.y - radius - 0.05D, node.z - radius,
                node.x + radius, node.y + radius, node.z + radius);

        for (VoxelShape shape : world.getBlockCollisions(null, box)) {
            for (Box b : shape.getBoundingBoxes()) {
                if (box.intersects(b)) {
                    // Resting on top of solid surface (floor, stairs, slab)
                    if (node.prevY >= b.maxY - 0.25D) {
                        node.y = b.maxY + radius;
                        node.onGround = true;

                        // Tangential ground friction damping
                        double vx = node.x - node.prevX;
                        double vz = node.z - node.prevZ;
                        node.prevX = node.x - vx * 0.45D;
                        node.prevZ = node.z - vz * 0.45D;

                        // Cancel downward penetration velocity so it does not bounce up next tick
                        if (node.y > node.prevY) {
                            node.prevY = node.y;
                        }
                    } else if (node.prevY + radius <= b.minY + 0.1D) {
                        // Hitting ceiling from below
                        node.y = b.minY - radius;
                        if (node.y < node.prevY) {
                            node.prevY = node.y;
                        }
                    } else {
                        // Push horizontally out of walls
                        double dxLeft = (node.x + radius) - b.minX;
                        double dxRight = b.maxX - (node.x - radius);
                        double dzNear = (node.z + radius) - b.minZ;
                        double dzFar = b.maxZ - (node.z - radius);

                        double minXOverlap = Math.min(dxLeft, dxRight);
                        double minZOverlap = Math.min(dzNear, dzFar);

                        if (minXOverlap < minZOverlap) {
                            node.x = (dxLeft < dxRight) ? b.minX - radius : b.maxX + radius;
                            // Cancel horizontal wall penetration velocity to prevent elastic rebound oscillation
                            node.prevX = node.x;
                        } else {
                            node.z = (dzNear < dzFar) ? b.minZ - radius : b.maxZ + radius;
                            // Cancel horizontal wall penetration velocity to prevent elastic rebound oscillation
                            node.prevZ = node.z;
                        }

                        // Wall friction on vertical sliding
                        double vy = node.y - node.prevY;
                        node.prevY = node.y - vy * 0.6D;
                    }
                }
            }
        }
    }

    public boolean isAnyPartOnGround(World world) {
        if (pelvis.onGround || chest.onGround || rightFoot.onGround || leftFoot.onGround || head.onGround || rightHand.onGround || leftHand.onGround) {
            return true;
        }
        // Direct world block check fallback
        double lowestY = Math.min(pelvis.y - 0.16D, Math.min(chest.y - 0.18D, Math.min(rightFoot.y - 0.14D, leftFoot.y - 0.14D)));
        BlockPos pos = BlockPos.ofFloored(pelvis.x, lowestY, pelvis.z);
        if (!world.getBlockState(pos).isAir()) return true;
        BlockPos down = pos.down();
        return !world.getBlockState(down).isAir() && (lowestY - (down.getY() + 1.0D)) < 0.35D;
    }

    public boolean isAnyPartOnGround() {
        return pelvis.onGround || chest.onGround || rightFoot.onGround || leftFoot.onGround || head.onGround || rightHand.onGround || leftHand.onGround;
    }

    public Vec3d getDraggingGroundContactPos() {
        if (pelvis.onGround) return new Vec3d(pelvis.x, pelvis.y, pelvis.z);
        if (chest.onGround) return new Vec3d(chest.x, chest.y, chest.z);
        if (rightFoot.onGround && leftFoot.onGround) {
            return new Vec3d((rightFoot.x + leftFoot.x) * 0.5D, (rightFoot.y + leftFoot.y) * 0.5D, (rightFoot.z + leftFoot.z) * 0.5D);
        }
        if (rightFoot.onGround) return new Vec3d(rightFoot.x, rightFoot.y, rightFoot.z);
        if (leftFoot.onGround) return new Vec3d(leftFoot.x, leftFoot.y, leftFoot.z);
        if (head.onGround) return new Vec3d(head.x, head.y, head.z);
        if (rightHand.onGround) return new Vec3d(rightHand.x, rightHand.y, rightHand.z);
        if (leftHand.onGround) return new Vec3d(leftHand.x, leftHand.y, leftHand.z);
        return new Vec3d(pelvis.x, pelvis.y, pelvis.z);
    }

    public Node getGroundContactNode() {
        Node best = null;
        double lowestY = Double.MAX_VALUE;
        Node[] nodes = { pelvis, chest, head, rightFoot, leftFoot, rightHand, leftHand };
        for (Node n : nodes) {
            if (n.onGround && n.y < lowestY) {
                lowestY = n.y;
                best = n;
            }
        }
        if (best != null) return best;

        best = pelvis;
        lowestY = pelvis.y;
        for (Node n : nodes) {
            if (n.y < lowestY) {
                lowestY = n.y;
                best = n;
            }
        }
        return best;
    }

    private void savePreviousState() {
        pelvis.savePrevious();
        chest.savePrevious();
        head.savePrevious();
        rightHand.savePrevious();
        leftHand.savePrevious();
        rightFoot.savePrevious();
        leftFoot.savePrevious();

        prevBodyYaw = bodyYaw;
        prevBodyPitch = bodyPitch;
        prevBodyRoll = bodyRoll;

        prevHeadPitch = headPitch;
        prevHeadYaw = headYaw;
        prevHeadRoll = headRoll;

        prevRightArmPitch = rightArmPitch;
        prevRightArmYaw = rightArmYaw;
        prevRightArmRoll = rightArmRoll;

        prevLeftArmPitch = leftArmPitch;
        prevLeftArmYaw = leftArmYaw;
        prevLeftArmRoll = leftArmRoll;

        prevRightLegPitch = rightLegPitch;
        prevRightLegYaw = rightLegYaw;
        prevRightLegRoll = rightLegRoll;

        prevLeftLegPitch = leftLegPitch;
        prevLeftLegYaw = leftLegYaw;
        prevLeftLegRoll = leftLegRoll;
    }

    /**
     * Mathematical projection: transforms limb vectors into the torso's local reference frame
     * using the inverse rotation matrix. Eliminates all gimbal lock, inversions, and flapping.
     */
    private void computeModelTransforms() {
        // Spine vector from pelvis to chest
        double sx = chest.x - pelvis.x;
        double sy = chest.y - pelvis.y;
        double sz = chest.z - pelvis.z;

        double horizontalDist = Math.sqrt(sx * sx + sz * sz);
        this.bodyYaw = (float) Math.toDegrees(Math.atan2(sx, sz));
        this.bodyPitch = (float) Math.toDegrees(Math.atan2(horizontalDist, Math.max(0.001D, sy)));

        // Keep body roll neutral to eliminate positive feedback oscillation from hanging feet
        this.bodyRoll = 0.0F;

        // Build orthonormal frame: uS (spine up towards neck), uF (front out of chest), uR (right)
        Vec3d uS = new Vec3d(sx, sy, sz).normalize();
        Vec3d uR = computeTorsoRight(new Vec3d(sx, sy, sz), bodyYaw);
        Vec3d uF = uS.crossProduct(uR).normalize();

        // Shoulder & Hip world anchors (+uR for Right, -uR for Left)
        Vec3d rShoulder = new Vec3d(chest.x + uR.x * 0.22D, chest.y + uR.y * 0.22D, chest.z + uR.z * 0.22D);
        Vec3d lShoulder = new Vec3d(chest.x - uR.x * 0.22D, chest.y - uR.y * 0.22D, chest.z - uR.z * 0.22D);
        Vec3d rHip = new Vec3d(pelvis.x + uR.x * 0.16D, pelvis.y + uR.y * 0.16D, pelvis.z + uR.z * 0.16D);
        Vec3d lHip = new Vec3d(pelvis.x - uR.x * 0.16D, pelvis.y - uR.y * 0.16D, pelvis.z - uR.z * 0.16D);

        // Right Arm
        Vec3d rArmVec = new Vec3d(rightHand.x - rShoulder.x, rightHand.y - rShoulder.y, rightHand.z - rShoulder.z);
        double rArmS = rArmVec.dotProduct(uS);
        double rArmF = rArmVec.dotProduct(uF);
        double rArmR = rArmVec.dotProduct(uR);
        double rArmY = -rArmS; // down towards feet
        rightArmPitch = (float) Math.atan2(rArmF, Math.max(0.01D, rArmY));
        rightArmRoll = (float) Math.atan2(rArmR, Math.hypot(rArmY, rArmF));
        rightArmYaw = 0.0F;

        // Left Arm
        Vec3d lArmVec = new Vec3d(leftHand.x - lShoulder.x, leftHand.y - lShoulder.y, leftHand.z - lShoulder.z);
        double lArmS = lArmVec.dotProduct(uS);
        double lArmF = lArmVec.dotProduct(uF);
        double lArmR = lArmVec.dotProduct(uR);
        double lArmY = -lArmS;
        leftArmPitch = (float) Math.atan2(lArmF, Math.max(0.01D, lArmY));
        leftArmRoll = (float) Math.atan2(-lArmR, Math.hypot(lArmY, lArmF));
        leftArmYaw = 0.0F;

        // Right Leg
        Vec3d rLegVec = new Vec3d(rightFoot.x - rHip.x, rightFoot.y - rHip.y, rightFoot.z - rHip.z);
        double rLegS = rLegVec.dotProduct(uS);
        double rLegF = rLegVec.dotProduct(uF);
        double rLegR = rLegVec.dotProduct(uR);
        double rLegY = -rLegS;
        rightLegPitch = (float) Math.atan2(rLegF, Math.max(0.01D, rLegY));
        rightLegRoll = (float) Math.atan2(rLegR, Math.hypot(rLegY, rLegF));
        rightLegYaw = 0.0F;

        // Left Leg
        Vec3d lLegVec = new Vec3d(leftFoot.x - lHip.x, leftFoot.y - lHip.y, leftFoot.z - lHip.z);
        double lLegS = lLegVec.dotProduct(uS);
        double lLegF = lLegVec.dotProduct(uF);
        double lLegR = lLegVec.dotProduct(uR);
        double lLegY = -lLegS;
        leftLegPitch = (float) Math.atan2(lLegF, Math.max(0.01D, lLegY));
        leftLegRoll = (float) Math.atan2(-lLegR, Math.hypot(lLegY, lLegF));
        leftLegYaw = 0.0F;

        // Head
        Vec3d headVec = new Vec3d(head.x - chest.x, head.y - chest.y, head.z - chest.z);
        double headS = headVec.dotProduct(uS);
        double headF = headVec.dotProduct(uF);
        double headR = headVec.dotProduct(uR);
        headPitch = (float) Math.atan2(headF, Math.max(0.01D, headS));
        headRoll = (float) Math.atan2(headR, Math.max(0.01D, headS));
        headYaw = 0.0F;
    }

    // =========================================================================
    // Independent Part Bounding Boxes
    // =========================================================================

    public Box getHeadBox() {
        return new Box(head.x - 0.28D, head.y - 0.28D, head.z - 0.28D,
                head.x + 0.28D, head.y + 0.28D, head.z + 0.28D);
    }

    public Box getTorsoBox() {
        return makeTorsoBox(pelvis.x, pelvis.y, pelvis.z, chest.x, chest.y, chest.z, bodyYaw);
    }

    public Box getRightArmBox() {
        Vec3d shoulder = getRightShoulderPos();
        return makeBoxBetween(shoulder, new Vec3d(rightHand.x, rightHand.y, rightHand.z), 0.18D);
    }

    public Box getLeftArmBox() {
        Vec3d shoulder = getLeftShoulderPos();
        return makeBoxBetween(shoulder, new Vec3d(leftHand.x, leftHand.y, leftHand.z), 0.18D);
    }

    public Box getRightLegBox() {
        Vec3d hip = getRightHipPos();
        return makeBoxBetween(hip, new Vec3d(rightFoot.x, rightFoot.y, rightFoot.z), 0.18D);
    }

    public Box getLeftLegBox() {
        Vec3d hip = getLeftHipPos();
        return makeBoxBetween(hip, new Vec3d(leftFoot.x, leftFoot.y, leftFoot.z), 0.18D);
    }

    private Box makeTorsoBox(double px, double py, double pz, double cx, double cy, double cz, float yaw) {
        Vec3d spine = new Vec3d(cx - px, cy - py, cz - pz);
        double len = spine.length();
        Vec3d uUp = len > 0.001D ? spine.multiply(1.0D / len) : new Vec3d(0.0D, 1.0D, 0.0D);

        Vec3d uRight = computeTorsoRight(spine, yaw);
        Vec3d uFront = uUp.crossProduct(uRight).normalize();

        double rx = uRight.x * 0.25D;
        double ry = uRight.y * 0.25D;
        double rz = uRight.z * 0.25D;
        double fx = uFront.x * 0.125D;
        double fy = uFront.y * 0.125D;
        double fz = uFront.z * 0.125D;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (int i = 0; i < 8; i++) {
            double ox = (i & 1) == 0 ? cx : px;
            double oy = (i & 1) == 0 ? cy : py;
            double oz = (i & 1) == 0 ? cz : pz;

            double sx = (i & 2) == 0 ? rx : -rx;
            double sy = (i & 2) == 0 ? ry : -ry;
            double sz = (i & 2) == 0 ? rz : -rz;

            double dx = (i & 4) == 0 ? fx : -fx;
            double dy = (i & 4) == 0 ? fy : -fy;
            double dz = (i & 4) == 0 ? fz : -fz;

            double x = ox + sx + dx;
            double y = oy + sy + dy;
            double z = oz + sz + dz;

            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public Box getFullBoundingBox() {
        return getHeadBox().union(getTorsoBox())
                .union(getRightArmBox()).union(getLeftArmBox())
                .union(getRightLegBox()).union(getLeftLegBox());
    }

    private Box makeBoxBetween(Node a, Node b, double margin) {
        return new Box(
                Math.min(a.x, b.x) - margin, Math.min(a.y, b.y) - margin, Math.min(a.z, b.z) - margin,
                Math.max(a.x, b.x) + margin, Math.max(a.y, b.y) + margin, Math.max(a.z, b.z) + margin
        );
    }

    private Box makeBoxBetween(Vec3d a, Vec3d b, double margin) {
        return new Box(
                Math.min(a.x, b.x) - margin, Math.min(a.y, b.y) - margin, Math.min(a.z, b.z) - margin,
                Math.max(a.x, b.x) + margin, Math.max(a.y, b.y) + margin, Math.max(a.z, b.z) + margin
        );
    }

    public Vec3d getRightShoulderPos() {
        Vec3d spine = new Vec3d(chest.x - pelvis.x, chest.y - pelvis.y, chest.z - pelvis.z);
        Vec3d uRight = computeTorsoRight(spine, bodyYaw);
        return new Vec3d(chest.x + uRight.x * 0.24D, chest.y + uRight.y * 0.24D, chest.z + uRight.z * 0.24D);
    }

    public Vec3d getLeftShoulderPos() {
        Vec3d spine = new Vec3d(chest.x - pelvis.x, chest.y - pelvis.y, chest.z - pelvis.z);
        Vec3d uRight = computeTorsoRight(spine, bodyYaw);
        return new Vec3d(chest.x - uRight.x * 0.24D, chest.y - uRight.y * 0.24D, chest.z - uRight.z * 0.24D);
    }

    public Vec3d getRightHipPos() {
        Vec3d spine = new Vec3d(chest.x - pelvis.x, chest.y - pelvis.y, chest.z - pelvis.z);
        Vec3d uRight = computeTorsoRight(spine, bodyYaw);
        return new Vec3d(pelvis.x + uRight.x * 0.14D, pelvis.y + uRight.y * 0.14D, pelvis.z + uRight.z * 0.14D);
    }

    public Vec3d getLeftHipPos() {
        Vec3d spine = new Vec3d(chest.x - pelvis.x, chest.y - pelvis.y, chest.z - pelvis.z);
        Vec3d uRight = computeTorsoRight(spine, bodyYaw);
        return new Vec3d(pelvis.x - uRight.x * 0.14D, pelvis.y - uRight.y * 0.14D, pelvis.z - uRight.z * 0.14D);
    }

    // =========================================================================
    // Sub-Tick Interpolation Getters (60+ FPS)
    // =========================================================================

    public Vec3d getInterpolatedPelvis(float tickDelta) {
        return pelvis.getInterpolated(tickDelta);
    }

    public Vec3d getInterpolatedChest(float tickDelta) {
        return chest.getInterpolated(tickDelta);
    }

    public Vec3d getInterpolatedHead(float tickDelta) {
        return head.getInterpolated(tickDelta);
    }

    public Vec3d getInterpolatedRightHand(float tickDelta) {
        return rightHand.getInterpolated(tickDelta);
    }

    public Vec3d getInterpolatedLeftHand(float tickDelta) {
        return leftHand.getInterpolated(tickDelta);
    }

    public Vec3d getInterpolatedRightFoot(float tickDelta) {
        return rightFoot.getInterpolated(tickDelta);
    }

    public Vec3d getInterpolatedLeftFoot(float tickDelta) {
        return leftFoot.getInterpolated(tickDelta);
    }

    public Vec3d getInterpolatedRightShoulder(float tickDelta) {
        Vec3d p = getInterpolatedPelvis(tickDelta);
        Vec3d c = getInterpolatedChest(tickDelta);
        Vec3d spine = c.subtract(p);
        Vec3d uRight = computeTorsoRight(spine, getInterpolatedBodyYaw(tickDelta));
        return c.add(uRight.multiply(0.24D));
    }

    public Vec3d getInterpolatedLeftShoulder(float tickDelta) {
        Vec3d p = getInterpolatedPelvis(tickDelta);
        Vec3d c = getInterpolatedChest(tickDelta);
        Vec3d spine = c.subtract(p);
        Vec3d uRight = computeTorsoRight(spine, getInterpolatedBodyYaw(tickDelta));
        return c.subtract(uRight.multiply(0.24D));
    }

    public Vec3d getInterpolatedRightHip(float tickDelta) {
        Vec3d p = getInterpolatedPelvis(tickDelta);
        Vec3d c = getInterpolatedChest(tickDelta);
        Vec3d spine = c.subtract(p);
        Vec3d uRight = computeTorsoRight(spine, getInterpolatedBodyYaw(tickDelta));
        return p.add(uRight.multiply(0.14D));
    }

    public Vec3d getInterpolatedLeftHip(float tickDelta) {
        Vec3d p = getInterpolatedPelvis(tickDelta);
        Vec3d c = getInterpolatedChest(tickDelta);
        Vec3d spine = c.subtract(p);
        Vec3d uRight = computeTorsoRight(spine, getInterpolatedBodyYaw(tickDelta));
        return p.subtract(uRight.multiply(0.14D));
    }

    public Box getInterpolatedHeadBox(float tickDelta) {
        Vec3d p = head.getInterpolated(tickDelta);
        return new Box(p.x - 0.25D, p.y - 0.25D, p.z - 0.25D, p.x + 0.25D, p.y + 0.25D, p.z + 0.25D);
    }

    public Box getInterpolatedTorsoBox(float tickDelta) {
        Vec3d p = pelvis.getInterpolated(tickDelta);
        Vec3d c = chest.getInterpolated(tickDelta);
        return makeTorsoBox(p.x, p.y, p.z, c.x, c.y, c.z, getInterpolatedBodyYaw(tickDelta));
    }

    public Box getInterpolatedRightArmBox(float tickDelta) {
        Vec3d h = rightHand.getInterpolated(tickDelta);
        Vec3d s = getInterpolatedRightShoulder(tickDelta);
        return makeBoxBetween(s, h, 0.11D);
    }

    public Box getInterpolatedLeftArmBox(float tickDelta) {
        Vec3d h = leftHand.getInterpolated(tickDelta);
        Vec3d s = getInterpolatedLeftShoulder(tickDelta);
        return makeBoxBetween(s, h, 0.11D);
    }

    public Box getInterpolatedRightLegBox(float tickDelta) {
        Vec3d f = rightFoot.getInterpolated(tickDelta);
        Vec3d hip = getInterpolatedRightHip(tickDelta);
        return makeBoxBetween(hip, f, 0.12D);
    }

    public Box getInterpolatedLeftLegBox(float tickDelta) {
        Vec3d f = leftFoot.getInterpolated(tickDelta);
        Vec3d hip = getInterpolatedLeftHip(tickDelta);
        return makeBoxBetween(hip, f, 0.12D);
    }

    public float getInterpolatedBodyPitch(float tickDelta) {
        return lerp(prevBodyPitch, bodyPitch, tickDelta);
    }

    public float getInterpolatedBodyRoll(float tickDelta) {
        return lerp(prevBodyRoll, bodyRoll, tickDelta);
    }

    public float getInterpolatedBodyYaw(float tickDelta) {
        return MathHelper.lerpAngleDegrees(tickDelta, prevBodyYaw, bodyYaw);
    }

    public float getInterpolatedHeadPitch(float tickDelta) {
        return lerp(prevHeadPitch, headPitch, tickDelta);
    }

    public float getInterpolatedHeadYaw(float tickDelta) {
        return lerp(prevHeadYaw, headYaw, tickDelta);
    }

    public float getInterpolatedHeadRoll(float tickDelta) {
        return lerp(prevHeadRoll, headRoll, tickDelta);
    }

    public float getInterpolatedRightArmPitch(float tickDelta) {
        return lerp(prevRightArmPitch, rightArmPitch, tickDelta);
    }

    public float getInterpolatedRightArmYaw(float tickDelta) {
        return lerp(prevRightArmYaw, rightArmYaw, tickDelta);
    }

    public float getInterpolatedRightArmRoll(float tickDelta) {
        return lerp(prevRightArmRoll, rightArmRoll, tickDelta);
    }

    public float getInterpolatedLeftArmPitch(float tickDelta) {
        return lerp(prevLeftArmPitch, leftArmPitch, tickDelta);
    }

    public float getInterpolatedLeftArmYaw(float tickDelta) {
        return lerp(prevLeftArmYaw, leftArmYaw, tickDelta);
    }

    public float getInterpolatedLeftArmRoll(float tickDelta) {
        return lerp(prevLeftArmRoll, leftArmRoll, tickDelta);
    }

    public float getInterpolatedRightLegPitch(float tickDelta) {
        return lerp(prevRightLegPitch, rightLegPitch, tickDelta);
    }

    public float getInterpolatedRightLegYaw(float tickDelta) {
        return lerp(prevRightLegYaw, rightLegYaw, tickDelta);
    }

    public float getInterpolatedRightLegRoll(float tickDelta) {
        return lerp(prevRightLegRoll, rightLegRoll, tickDelta);
    }

    public float getInterpolatedLeftLegPitch(float tickDelta) {
        return lerp(prevLeftLegPitch, leftLegPitch, tickDelta);
    }

    public float getInterpolatedLeftLegYaw(float tickDelta) {
        return lerp(prevLeftLegYaw, leftLegYaw, tickDelta);
    }

    public float getInterpolatedLeftLegRoll(float tickDelta) {
        return lerp(prevLeftLegRoll, leftLegRoll, tickDelta);
    }

    private static float lerp(float a, float b, float delta) {
        return a + delta * (b - a);
    }
}
