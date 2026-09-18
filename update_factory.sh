sed -i 's/"gemini" -> GeminiProvider(config)/"gemini" -> GeminiProvider(config)\n            id if id.startsWith("custom_gemini") -> GeminiProvider(config)/' app/src/main/java/com/example/data/ai/ConcreteProviders.kt
sed -i 's/"anthropic" -> AnthropicProvider(config)/"anthropic" -> AnthropicProvider(config)\n            id if id.startsWith("custom_anthropic") -> AnthropicProvider(config)/' app/src/main/java/com/example/data/ai/ConcreteProviders.kt
sed -i 's/when (config.id) {/when (val id = config.id) {/' app/src/main/java/com/example/data/ai/ConcreteProviders.kt
