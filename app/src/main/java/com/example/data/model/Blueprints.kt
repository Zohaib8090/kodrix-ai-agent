package com.example.data.model

object BlueprintsData {

    val androidBlueprints = listOf(
        Blueprint(
            id = "android_tasks",
            title = "Task & Habit Tracker",
            description = "Material 3 todo list with streak counters, priority tags, and Room DB persistence",
            iconName = "check_circle",
            platform = PlatformType.ANDROID,
            defaultPrompt = "A modern task and habit tracker with daily checklist, priority categories (High, Medium, Low), reminder alerts, and persistent Room database storage.",
            defaultFeatures = listOf("Room DB Persistence", "Dark Theme Default", "Bottom Navigation")
        ),
        Blueprint(
            id = "android_notes",
            title = "Markdown Note Keeper",
            description = "Rich Markdown note editor with tag indexing, search filter, and instant auto-save",
            iconName = "edit_note",
            platform = PlatformType.ANDROID,
            defaultPrompt = "A clean Markdown notebook app with real-time preview, folder organization, search filtering, and offline Room DB sync.",
            defaultFeatures = listOf("Room DB Persistence", "Dark Theme Default", "Offline Cache")
        ),
        Blueprint(
            id = "android_expenses",
            title = "Expense & Budget Tracker",
            description = "Personal finance tracker with monthly budget limits, visual breakdown, and categories",
            iconName = "account_balance_wallet",
            platform = PlatformType.ANDROID,
            defaultPrompt = "A personal finance manager tracking income and expenses across food, transport, and bills, with monthly budget progress bars and Room DB logging.",
            defaultFeatures = listOf("Room DB Persistence", "Dark Theme Default", "Bottom Navigation")
        ),
        Blueprint(
            id = "android_weather",
            title = "Weather Companion",
            description = "Real-time forecast with 5-day outlook, wind/humidity telemetry, and location caching",
            iconName = "wb_sunny",
            platform = PlatformType.ANDROID,
            defaultPrompt = "A sleek weather dashboard displaying current temperature, hourly forecast, 5-day outlook, dynamic weather animations, and cached city data.",
            defaultFeatures = listOf("Dark Theme Default", "Offline Cache")
        ),
        Blueprint(
            id = "android_camera",
            title = "Camera & Photo Journal",
            description = "Capture snapshots, attach captions, and organize personal memories into a feed",
            iconName = "photo_camera",
            platform = PlatformType.ANDROID,
            defaultPrompt = "A photo journal app with camera integration, photo picker, caption editing, and chronological feed with Room database storage.",
            defaultFeatures = listOf("Room DB Persistence", "Camera / Photo Picker", "Dark Theme Default")
        ),
        Blueprint(
            id = "android_custom",
            title = "Blank Custom App",
            description = "Clean slate native Jetpack Compose template ready for any custom prompt",
            iconName = "add_circle_outline",
            platform = PlatformType.ANDROID,
            defaultPrompt = "A native Android application built with modern Jetpack Compose, Material 3 design, and clean MVVM architecture.",
            defaultFeatures = listOf("Room DB Persistence", "Dark Theme Default")
        )
    )

    val webBlueprints = listOf(
        Blueprint(
            id = "web_saas",
            title = "SaaS Landing & Dashboard",
            description = "High-converting modern landing page with pricing table, hero banner, and app dashboard",
            iconName = "dashboard",
            platform = PlatformType.WEB,
            framework = WebFramework.REACT_VITE,
            defaultPrompt = "A modern SaaS web application with a responsive hero section, feature grid, interactive pricing calculator, and a customer analytics dashboard.",
            defaultFeatures = listOf("Responsive Layout", "Dark Theme Default", "Interactive State")
        ),
        Blueprint(
            id = "web_kanban",
            title = "Interactive Kanban Board",
            description = "Agile task board with drag-and-drop columns (To Do, In Progress, Review, Done)",
            iconName = "view_kanban",
            platform = PlatformType.WEB,
            framework = WebFramework.REACT_VITE,
            defaultPrompt = "A responsive Kanban board application with draggable cards, column status updates, member assignment avatars, and local storage persistence.",
            defaultFeatures = listOf("Local Persistence", "Dark Theme Default", "Interactive State")
        ),
        Blueprint(
            id = "web_docs",
            title = "Documentation Portal",
            description = "Developer documentation portal with syntax highlighting, search, and sidebar navigation",
            iconName = "menu_book",
            platform = PlatformType.WEB,
            framework = WebFramework.NEXT_JS,
            defaultPrompt = "A clean documentation site with responsive sidebar navigation, instant article search, code syntax highlighting blocks, and copy-to-clipboard buttons.",
            defaultFeatures = listOf("Sidebar Navigation", "Dark Theme Default", "Search Filter")
        ),
        Blueprint(
            id = "web_ecommerce",
            title = "Product Catalog & Cart",
            description = "Online storefront with product grid, category filters, and live shopping cart drawer",
            iconName = "shopping_cart",
            platform = PlatformType.WEB,
            framework = WebFramework.VUE_VITE,
            defaultPrompt = "An e-commerce product catalog featuring search, category pills, responsive product cards, quantity adjusters, and a flyout shopping cart drawer.",
            defaultFeatures = listOf("Shopping Cart Drawer", "Dark Theme Default", "Local Persistence")
        ),
        Blueprint(
            id = "web_custom",
            title = "Blank Web Application",
            description = "Fresh modern Single Page Application ready for custom prompt",
            iconName = "language",
            platform = PlatformType.WEB,
            framework = WebFramework.REACT_VITE,
            defaultPrompt = "A fast and responsive web application built with modern HTML5, CSS3, and JavaScript.",
            defaultFeatures = listOf("Responsive Layout", "Dark Theme Default")
        )
    )

    fun getBlueprints(platform: PlatformType): List<Blueprint> {
        return if (platform == PlatformType.ANDROID) androidBlueprints else webBlueprints
    }
}
