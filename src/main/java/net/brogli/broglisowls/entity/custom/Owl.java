package net.brogli.broglisowls.entity.custom;

import net.brogli.broglisowls.entity.BroglisOwlsEntityTypes;
import net.brogli.broglisowls.item.BroglisOwlsItems;
import net.brogli.broglisowls.sound.BroglisOwlsSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import java.util.UUID;
import java.util.function.Predicate;

public class Owl extends Animal implements IAnimatable {

    private final AnimationFactory factory = new AnimationFactory(this);

    public int eggTime = this.random.nextInt(6000) + 6000;

    public float flap;
    public float flapSpeed;
    public float oFlapSpeed;
    public float oFlap;
    private float flapping = 1.0F;
    private float nextFlap = 1.0F;

    public static final Predicate<LivingEntity> PREY_SELECTOR = (entity) -> {
        EntityType<?> entitytype = entity.getType();
        return entitytype == EntityType.RABBIT;
    };

    private float shakeAnim;
    private float shakeAnimO;
    private boolean isWet;
    private boolean isShaking;


    public Owl(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    private static final UUID SPEED_MODIFIER_BABY_UUID = UUID.fromString("cc46a8de-a578-4193-901f-42a3a882094a");
    private static final UUID FLY_MODIFIER_BABY_UUID = UUID.fromString("7b243d6f-78fd-4bfd-bc1a-8148683cc91e");
    private static final UUID ATK_MODIFIER_BABY_UUID = UUID.fromString("3425f101-4799-4905-8a5c-320f9fe6854e");

    private static final AttributeModifier SPEED_MODIFIER_BABY = new AttributeModifier(SPEED_MODIFIER_BABY_UUID, "slow baby move", -0.03D, AttributeModifier.Operation.ADDITION);
    private static final AttributeModifier FLY_MODIFIER_BABY = new AttributeModifier(FLY_MODIFIER_BABY_UUID, "slow baby fly", -0.1D, AttributeModifier.Operation.ADDITION);
    private static final AttributeModifier ATK_MODIFIER_BABY = new AttributeModifier(ATK_MODIFIER_BABY_UUID, "soft baby atk", -1.0D, AttributeModifier.Operation.ADDITION);

    public static AttributeSupplier setAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.FLYING_SPEED, (double) 0.5F)
                .add(Attributes.MOVEMENT_SPEED, (double) 0.2F)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D).build();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.25D, Ingredient.of(Items.RABBIT), false));
        this.goalSelector.addGoal(2, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 32.0F));
        this.targetSelector.addGoal(10, new NearestAttackableTargetGoal<>(this, Animal.class, false, PREY_SELECTOR));
        this.goalSelector.addGoal(15, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(30, new OwlWanderGoal(this, 1.0D));
    }

    @Override
    public void setBaby(boolean isBaby) {
        super.setBaby(isBaby);
        if (this.level != null && !this.level.isClientSide) {
            AttributeInstance movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
            AttributeInstance fly = this.getAttribute(Attributes.FLYING_SPEED);
            AttributeInstance atk = this.getAttribute(Attributes.ATTACK_DAMAGE);

            movement.removeModifier(SPEED_MODIFIER_BABY);
            fly.removeModifier(FLY_MODIFIER_BABY);
            atk.removeModifier(ATK_MODIFIER_BABY);
            if (isBaby) {
                movement.addTransientModifier(SPEED_MODIFIER_BABY);
                fly.addTransientModifier(FLY_MODIFIER_BABY);
                atk.addTransientModifier(ATK_MODIFIER_BABY);
            }
        }

    }

    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);
        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(true);
        flyingpathnavigation.setCanPassDoors(true);
        return flyingpathnavigation;
    }

    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("age", this.age);
        tag.putInt("eggLayTime", this.eggTime);
    }

    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setAge(tag.getInt("age"));
        if (tag.contains("eggLayTime")) {
            this.eggTime = tag.getInt("eggLayTime");
        }
    }

    public void aiStep() {

        if (!this.level.isClientSide && this.isWet && !this.isShaking && !this.isPathFinding() && this.onGround) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
            this.level.broadcastEntityEvent(this, (byte) 8);
        }

        if (this.level.random.nextInt(500) == 0) {
            this.playSound(this.isBaby() ? BroglisOwlsSounds.BABY_OWL_AMBIENT_1.get() : BroglisOwlsSounds.OWL_AMBIENT_1.get(), this.isBaby() ? 0.3F : 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }

        if (!this.level.isClientSide && !this.isBaby() && this.isAlive() && --this.eggTime <= 0) {
            this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            this.spawnAtLocation(BroglisOwlsItems.ITEM_OWL_EGG.get());
            this.gameEvent(GameEvent.ENTITY_PLACE);
            this.eggTime = this.random.nextInt(6000) + 6000;
        }
        super.aiStep();
        this.calculateFlapping();

    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        this.playSound(SoundEvents.WOLF_SHAKE, 0.15F, 1.0F);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (this.isFood(itemstack) && this.isBaby()) {
            this.feed(player, itemstack);
            return InteractionResult.sidedSuccess(this.level.isClientSide);
        }
        return super.mobInteract(player, hand);
    }


    private void feed(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild)
            stack.shrink(1);
        this.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-this.getAge()));
        this.level.addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
    }

    public boolean shouldDropExperience() {
        return false;
    }

    public boolean canBreed() {
        return !isBaby();
    }

    private void calculateFlapping() {
        this.oFlap = this.flap;
        this.oFlapSpeed = this.flapSpeed;
        this.flapSpeed += (float) (!this.onGround && !this.isPassenger() ? 4 : -1) * 0.3F;
        this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
        if (!this.onGround && this.flapping < 1.0F) {
            this.flapping = 1.0F;
        }

        this.flapping *= 0.9F;
        Vec3 vec3 = this.getDeltaMovement();
        if (!this.onGround && vec3.y < 0.0D) {
            this.setDeltaMovement(vec3.multiply(1.0D, 0.6D, 1.0D));
        }

        this.flap += this.flapping * 2.0F;
    }

    protected boolean isFlapping() {
        return this.flyDist > this.nextFlap;
    }

    protected void onFlap() {
        this.playSound(SoundEvents.PARROT_FLY, 0.15F, 1.0F);
        this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
    }

    public boolean isFlying() {
        return !this.onGround;
    }

    public void tick() {
        super.tick();
        if (this.isAlive()) {

            if (this.isInWaterRainOrBubble()) {
                this.isWet = true;
                if (this.isShaking && !this.level.isClientSide) {
                    this.level.broadcastEntityEvent(this, (byte) 56);
                    this.cancelShake();
                }
            } else if ((this.isWet || this.isShaking) && this.isShaking) {
                if (this.shakeAnim == 0.0F) {
                    this.playSound(SoundEvents.WOLF_SHAKE, 0.3F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                    this.gameEvent(GameEvent.ENTITY_SHAKE);
                }

                this.shakeAnimO = this.shakeAnim;
                this.shakeAnim += 0.05F;
                if (this.shakeAnimO >= 2.0F) {
                    this.isWet = false;
                    this.isShaking = false;
                }

                if (this.shakeAnim > 0.4F) {
                    float f = (float) this.getY();
                    int i = (int) (Mth.sin((this.shakeAnim - 0.4F) * (float) Math.PI) * 7.0F);
                    Vec3 vec3 = this.getDeltaMovement();

                    for (int j = 0; j < i; ++j) {
                        float f1 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
                        float f2 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
                        this.level.addParticle(ParticleTypes.SPLASH, this.getX() + (double) f1, (double) (f + 0.8F), this.getZ() + (double) f2, vec3.x, vec3.y, vec3.z);
                    }
                }
            }

        }
    }

    public void handleEntityEvent(byte b) {
        if (b == 8) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
        } else if (b == 56) {
            this.cancelShake();
        } else {
            super.handleEntityEvent(b);
        }

    }

    private void cancelShake() {
        this.isShaking = false;
        this.shakeAnim = 0.0F;
        this.shakeAnimO = 0.0F;
    }

    public void die(DamageSource source) {
        this.isWet = false;
        this.isShaking = false;
        this.shakeAnimO = 0.0F;
        this.shakeAnim = 0.0F;
        super.die(source);
    }

    public boolean doHurtTarget(Entity entity) {
        boolean flag = entity.hurt(DamageSource.mobAttack(this), (float) ((int) this.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        if (flag) {
            this.doEnchantDamageEffects(this, entity);
        }

        return flag;
    }

    public boolean causeFallDamage(float a, float b, DamageSource source) {
        return false;
    }

    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack pStack) {
        return pStack.getItem() == Items.RABBIT;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob mob) {
        return BroglisOwlsEntityTypes.OWL_ENTITY_TYPE.get().create(serverLevel);
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        if (this.isShaking) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.entity_owl.ruffle", false));
            return PlayState.CONTINUE;
        }
        if (this.onGround && event.isMoving()) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.entity_owl.walk", true));
            return PlayState.CONTINUE;
        }
        if (this.isFlying()) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.entity_owl.flight", true));
            return PlayState.CONTINUE;
        }
        event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.entity_owl.idle", true));
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController(this, "controller", 5, this::predicate));
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    static class OwlWanderGoal extends WaterAvoidingRandomFlyingGoal {
        public OwlWanderGoal(PathfinderMob pathFinder, double distance) {
            super(pathFinder, distance);
        }

        @javax.annotation.Nullable
        protected Vec3 getPosition() {
            Vec3 vec3 = null;
            if (this.mob.isInWater()) {
                vec3 = LandRandomPos.getPos(this.mob, 15, 15);
            }

            if (this.mob.getRandom().nextFloat() >= this.probability) {
                vec3 = this.getTreePos();
            }

            return vec3 == null ? super.getPosition() : vec3;
        }

        @javax.annotation.Nullable
        private Vec3 getTreePos() {
            BlockPos blockpos = this.mob.blockPosition();
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos blockpos$mutableblockpos1 = new BlockPos.MutableBlockPos();

            for (BlockPos blockpos1 : BlockPos.betweenClosed(Mth.floor(this.mob.getX() - 3.0D), Mth.floor(this.mob.getY() - 6.0D), Mth.floor(this.mob.getZ() - 3.0D), Mth.floor(this.mob.getX() + 3.0D), Mth.floor(this.mob.getY() + 6.0D), Mth.floor(this.mob.getZ() + 3.0D))) {
                if (!blockpos.equals(blockpos1)) {
                    BlockState blockstate = this.mob.level.getBlockState(blockpos$mutableblockpos1.setWithOffset(blockpos1, Direction.DOWN));
                    boolean flag = blockstate.getBlock() instanceof LeavesBlock || blockstate.is(BlockTags.LOGS);
                    if (flag && this.mob.level.isEmptyBlock(blockpos1) && this.mob.level.isEmptyBlock(blockpos$mutableblockpos.setWithOffset(blockpos1, Direction.UP))) {
                        return Vec3.atBottomCenterOf(blockpos1);
                    }
                }
            }

            return null;
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose p_21047_) {
        EntityDimensions dim = this.getType().getDimensions();
        if (this.isBaby())
            dim = EntityDimensions.fixed(0.6F, 0.8F);
        return dim;
    }
}
