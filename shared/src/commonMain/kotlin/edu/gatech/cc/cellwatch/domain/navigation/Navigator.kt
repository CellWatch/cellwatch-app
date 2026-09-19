package edu.gatech.cc.cellwatch.domain.navigation

/**
 * The app's back stack, shared so both platforms navigate the same graph.
 *
 * Deliberately not a platform navigation component: UIKit and Android Views
 * each have their own, and letting each drive itself is how the two apps ended
 * up with different notions of what screens exist. Platforms render
 * [current] and forward user intent here.
 *
 * Not thread-safe; drive it from the UI thread like any other view state.
 */
class Navigator(start: Destination) {

    private val stack = ArrayDeque<Destination>().apply { addLast(start) }

    /** The screen that should be on screen now. Never empty. */
    val current: Destination get() = stack.last()

    /** Root first, [current] last. */
    val backStack: List<Destination> get() = stack.toList()

    val canGoBack: Boolean get() = stack.size > 1

    /**
     * Pushes [destination].
     *
     * Navigating to the screen already showing is ignored rather than stacked:
     * a double tap on a button is the common cause, and two identical entries
     * mean the user has to press back twice to leave one screen.
     */
    fun goTo(destination: Destination) {
        if (current == destination) return
        stack.addLast(destination)
    }

    /** Pops one entry. Returns false at the root, so callers can defer to platform back. */
    fun back(): Boolean {
        if (!canGoBack) return false
        stack.removeLast()
        return true
    }

    /**
     * Clears the stack and starts again at [destination].
     *
     * For transitions where returning would be wrong - finishing onboarding
     * should not leave onboarding behind the back button.
     */
    fun resetTo(destination: Destination) {
        stack.clear()
        stack.addLast(destination)
    }

    /** Pops to the root without discarding it. */
    fun backToRoot() {
        while (stack.size > 1) stack.removeLast()
    }
}
