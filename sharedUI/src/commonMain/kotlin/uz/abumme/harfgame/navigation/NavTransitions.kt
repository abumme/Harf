package uz.abumme.harfgame.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEvent.Companion.EDGE_RIGHT

/** Whether navigation animates on this platform: a slide on Android and iOS, an instant switch on desktop and web. */
internal expect val animatesNavigation: Boolean

private const val NAV_MS = 300

private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

// The page underneath moves a quarter of the width: enough parallax to read as depth, little enough to stay calm.
private fun parallax(width: Int) = width / 4

internal val navEnter: Scope.() -> EnterTransition = {
    if (!animatesNavigation) EnterTransition.None
    else slideIntoContainer(SlideDirection.Start, tween(NAV_MS, easing = FastOutSlowInEasing))
}

internal val navExit: Scope.() -> ExitTransition = {
    if (!animatesNavigation) ExitTransition.None
    else slideOutOfContainer(SlideDirection.Start, tween(NAV_MS, easing = FastOutSlowInEasing), ::parallax)
}

internal val navPopEnter: Scope.() -> EnterTransition = {
    if (!animatesNavigation) EnterTransition.None
    else slideIntoContainer(SlideDirection.End, tween(NAV_MS, easing = FastOutSlowInEasing), ::parallax)
}

internal val navPopExit: Scope.() -> ExitTransition = {
    if (!animatesNavigation) ExitTransition.None
    else slideOutOfContainer(SlideDirection.End, tween(NAV_MS, easing = FastOutSlowInEasing))
}

/**
 * A back swipe moves the pages away from the edge it started at (Android accepts either edge); a gesture with no
 * edge falls back to the button's direction.
 */
private fun backSwipeDirection(swipeEdge: Int): SlideDirection = when (swipeEdge) {
    EDGE_LEFT -> SlideDirection.Right
    EDGE_RIGHT -> SlideDirection.Left
    else -> SlideDirection.End
}

// The back gesture drives these, so linear easing keeps the page under the finger.
internal val navPredictivePopEnter: Scope.(swipeEdge: Int) -> EnterTransition = { edge ->
    if (!animatesNavigation) EnterTransition.None
    else slideIntoContainer(backSwipeDirection(edge), tween(NAV_MS, easing = LinearEasing), ::parallax)
}

internal val navPredictivePopExit: Scope.(swipeEdge: Int) -> ExitTransition = { edge ->
    if (!animatesNavigation) ExitTransition.None
    else slideOutOfContainer(backSwipeDirection(edge), tween(NAV_MS, easing = LinearEasing))
}
