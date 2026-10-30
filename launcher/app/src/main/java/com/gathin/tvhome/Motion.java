package com.gathin.tvhome;

import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** Reduced motion (config.xml reduced_motion): the one spring and the one time limit every animation uses then. */
final class Motion {

    /** The longest any reduced-motion animation runs, in milliseconds. */
    static final int REDUCED_MS = 90;
    /** Critically damped (no bounce) and stiff enough that any move, even across the screen, settles in 5 frames. */
    static final float REDUCED_STIFFNESS = 15000f;
    /** The spring stops once it is within this many pixels of its target. */
    static final float REDUCED_MIN_CHANGE = 2f;

    private Motion() {
    }

    static void reduce(SpringAnimation spring) {
        spring.setSpring(new SpringForce().setStiffness(REDUCED_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        spring.setMinimumVisibleChange(REDUCED_MIN_CHANGE);
    }

    /** Read configuration once per surface; system animation disable is checked at interaction time. */
    static final class Policy {
        final boolean enabled, reduced;
        Policy(android.content.Context context) {
            Config config = Config.load(context);
            enabled = config.animations; reduced = config.reducedMotion;
        }
        int duration(int normal) {
            if (!enabled || !android.animation.ValueAnimator.areAnimatorsEnabled()) return 0;
            return reduced ? Math.min(normal, REDUCED_MS) : normal;
        }
        boolean drift() { return duration(200) > REDUCED_MS; }
    }

    static void enter(android.app.Activity activity) {
        Policy policy = new Policy(activity);
        activity.overridePendingTransition(policy.duration(180) > REDUCED_MS ? R.anim.scene_enter : 0,
                policy.duration(180) > REDUCED_MS ? R.anim.scene_exit : 0);
    }
}
