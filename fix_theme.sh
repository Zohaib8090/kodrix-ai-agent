#!/bin/bash
find app/src/main/java/com/example/ui/screens -name "*.kt" -type f -print0 | xargs -0 sed -i \
  -e 's/LandingBackground/MaterialTheme.colorScheme.background/g' \
  -e 's/LandingCardBg/MaterialTheme.colorScheme.surface/g' \
  -e 's/LandingCategoryBg/MaterialTheme.colorScheme.surfaceVariant/g' \
  -e 's/LandingHeading/MaterialTheme.colorScheme.onSurface/g' \
  -e 's/LandingSubtext/MaterialTheme.colorScheme.onSurfaceVariant/g' \
  -e 's/LandingGrayText/MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)/g' \
  -e 's/LandingBorder/MaterialTheme.colorScheme.outline/g' \
  -e 's/LandingPeach/MaterialTheme.colorScheme.primaryContainer/g' \
  -e 's/Color(0xFFE8590C)/MaterialTheme.colorScheme.primary/g' \
  -e '/import com.example.ui.theme.Landing/d' \
  -e '/import com.example.ui.theme.Color/d'

find app/src/main/java/com/example/ui/common -name "*.kt" -type f -print0 | xargs -0 sed -i \
  -e 's/LandingBackground/MaterialTheme.colorScheme.background/g' \
  -e 's/LandingCardBg/MaterialTheme.colorScheme.surface/g' \
  -e 's/LandingCategoryBg/MaterialTheme.colorScheme.surfaceVariant/g' \
  -e 's/LandingHeading/MaterialTheme.colorScheme.onSurface/g' \
  -e 's/LandingSubtext/MaterialTheme.colorScheme.onSurfaceVariant/g' \
  -e 's/LandingGrayText/MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)/g' \
  -e 's/LandingBorder/MaterialTheme.colorScheme.outline/g' \
  -e 's/LandingPeach/MaterialTheme.colorScheme.primaryContainer/g' \
  -e 's/Color(0xFFE8590C)/MaterialTheme.colorScheme.primary/g' \
  -e '/import com.example.ui.theme.Landing/d' \
  -e '/import com.example.ui.theme.Color/d'
