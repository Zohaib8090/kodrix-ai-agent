#!/bin/bash

# Update PreferenceStorage.kt - getDefaultModelsForProvider
sed -i 's/listOf("gemini-2.5-flash", "gemini-1.5-pro", "gemini-2.0-flash-exp")/listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro-preview", "gemini-2.5-pro")/' app/src/main/java/com/example/data/local/PreferenceStorage.kt
sed -i 's/listOf("gpt-4o", "gpt-4o-mini", "o1-mini")/listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna", "gpt-5.5", "gpt-5.5-pro", "gpt-5.4", "gpt-5.4-mini")/' app/src/main/java/com/example/data/local/PreferenceStorage.kt
sed -i 's/listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")/listOf("claude-fable-5-1", "claude-mythos-5-1", "claude-sonnet-5")/' app/src/main/java/com/example/data/local/PreferenceStorage.kt
sed -i 's/listOf("llama-3.3-70b-versatile", "mixtral-8x7b-32768", "llama-3.1-8b-instant")/listOf("qwen\/qwen3.6-27b", "llama\/llama-4-scout-17b-16e-instruct", "llama\/llama-4-maverick-17b-128e-instruct", "gpt-oss-120b", "deepseek-r1-distill-llama-70b")/' app/src/main/java/com/example/data/local/PreferenceStorage.kt
sed -i 's/listOf("meta-llama\/Meta-Llama-3.1-70B-Instruct-Turbo", "deepseek-ai\/deepseek-coder", "mistralai\/Mixtral-8x7B-Instruct-v0.1")/listOf("deepseek\/deepseek-v3", "meta-llama\/llama-4-maverick", "qwen\/qwen3.6-27b")/' app/src/main/java/com/example/data/local/PreferenceStorage.kt

# Update PreferenceStorage.kt - getDefaultProviders defaultModels
sed -i 's/defaultModel = "gemini-2.5-flash"/defaultModel = "gemini-3.8-flash"/' app/src/main/java/com/example/data/local/PreferenceStorage.kt
sed -i 's/defaultModel = "gpt-4o"/defaultModel = "gpt-5.6-sol"/' app/src/main/java/com/example/data/local/PreferenceStorage.kt
sed -i 's/defaultModel = "claude-3-5-sonnet-20241022"/defaultModel = "claude-fable-5-1"/' app/src/main/java/com/example/data/local/PreferenceStorage.kt
sed -i 's/defaultModel = "llama-3.3-70b-versatile"/defaultModel = "qwen\/qwen3.6-27b"/' app/src/main/java/com/example/data/local/PreferenceStorage.kt
sed -i 's/defaultModel = "meta-llama\/Meta-Llama-3.1-70B-Instruct-Turbo"/defaultModel = "deepseek\/deepseek-v3"/' app/src/main/java/com/example/data/local/PreferenceStorage.kt

# Replace other occurrences of old models
sed -i 's/gemini-2.5-flash/gemini-3.8-flash/g' app/src/main/java/com/example/data/ai/ConcreteProviders.kt
sed -i 's/gemini-2.5-flash/gemini-3.8-flash/g' app/src/main/java/com/example/data/services/WorkflowService.kt
sed -i 's/gemini-2.5-flash/gemini-3.8-flash/g' app/src/main/java/com/example/ui/viewmodel/BuildTrackerViewModel.kt
