package io.github.wingedpanther.cleanmytv.home.customization

import io.github.wingedpanther.cleanmytv.home.tr

/** The editor's sections. [title] is the English, and the key for [label], the name the TV shows. */
enum class CustomizationCategory(val title: String, val subtitle: String) {
    LAYOUT("Layout", "Layout, space above the apps, sections and top bar icons"),
    ROWS("Rows and folders", "Make, name, fill and order your rows and folders"),
    LOOK("Look", "Colours, shading, icons, tile sizes and focus"),
    THEMES("Themes and wallpaper", "Themes, Bing's picture of the day, and the day and night looks"),
    WIDGETS("Widgets", "Clock, weather and the idle screen"),
    APPS("Apps", "The dock, apps per row, order and hidden apps"),
    BUTTONS("Remote buttons", "Give spare buttons a new job"),
    LOCK("App lock", "Lock apps behind a PIN"),
    VISUALIZER("Music bars", "Style, colour, height and sensitivity"),
    FIREPLACE("Fireplace", "The fire as the wallpaper, its sound and timer"),
    MORE("More", "Greeting, names, memory, inputs and power on");

    val label: String get() = tr(title)
}
