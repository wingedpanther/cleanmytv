package io.github.wingedpanther.cleanmytv.home.customization

enum class CustomizationCategory(val title: String, val subtitle: String) {
    LAYOUT(
        "Layout & Rows",
        "Choose launcher layout mode, top spacing, and section order"
    ),
    LOOK(
        "Look & Wallpaper",
        "Customize wallpaper background, accent colors, tile shape, and focus animations"
    ),
    WIDGETS(
        "Widgets & Header",
        "Configure clock format, weather temperature unit, and idle screensaver timer"
    ),
    APPS(
        "Apps & Organization",
        "Manage dock size, apps per row, D-pad app order, and hidden apps"
    ),
    VISUALIZER(
        "Music Visualizer",
        "Configure animated audio spectrum bars and color theme"
    ),
    FIREPLACE(
        "Fireplace Ambient",
        "Control live ambient log fire background, crackling audio, and timer"
    )
}
