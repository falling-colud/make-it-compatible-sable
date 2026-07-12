package dev.micsable.littletiles_sable.mixin;

import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import dev.ryanhcode.sable.api.physics.mass.MassTracker;

/**
 * Write access to {@code MassTracker}'s state so the end-of-tick tile mass rebuild can copy freshly computed
 * values <em>into the live tracker</em> instead of replacing it via {@code ServerSubLevel.buildMassTracker()}.
 * Replacing the tracker also replaces the {@code MergedMassTracker}, which loses its {@code lastCenterOfMass}
 * continuity - and that continuity is what {@code MergedMassTracker.uploadData} uses to teleport-compensate the
 * body when the center of mass (Sable's rotation point) moves. Without it, the first update after a rebuild
 * snapped the rotation point with zero compensation and the whole vehicle visibly jolted on every tile edit.
 */
@Mixin(MassTracker.class)
public interface MassTrackerAccessor {

    @Accessor("mass")
    void mic$setMass(double mass);

    @Accessor("inverseMass")
    void mic$setInverseMass(double inverseMass);

    @Accessor("centerOfMass")
    void mic$setCenterOfMass(Vector3d centerOfMass);

    @Accessor("inertiaTensor")
    void mic$setInertiaTensor(Matrix3d inertiaTensor);

    @Accessor("inverseInertiaTensor")
    void mic$setInverseInertiaTensor(Matrix3d inverseInertiaTensor);
}
