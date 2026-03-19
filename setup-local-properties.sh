#!/data/data/com.termux/files/usr/bin/bash
# Setup script to generate local.properties from tokens directory
# Usage: ./setup-local-properties.sh [tokens_dir]
# Default: looks for tokens at ~/docs/tokens/

set -e

TOKENS_DIR="${1:-$HOME/docs/tokens}"
LOCAL_PROPS="$(dirname "$0")/local.properties"

if [ ! -d "$TOKENS_DIR" ]; then
    echo "❌ Tokens directory not found: $TOKENS_DIR"
    echo "Usage: $0 [tokens_dir]"
    exit 1
fi

# Read API keys from tokens
GOOGLE_API_KEY=$(cat "$TOKENS_DIR/gemini.openclaw.v2.txt" 2>/dev/null || echo "")
OPENROUTER_API_KEY=$(cat "$TOKENS_DIR/openclaw-companion.txt" 2>/dev/null || echo "")

if [ -z "$GOOGLE_API_KEY" ]; then
    echo "⚠️  Warning: GOOGLE_API_KEY not found in $TOKENS_DIR/gemini.openclaw.v2.txt"
fi

if [ -z "$OPENROUTER_API_KEY" ]; then
    echo "⚠️  Warning: OPENROUTER_API_KEY not found in $TOKENS_DIR/openclaw-companion.txt"
fi

# Generate local.properties
cat > "$LOCAL_PROPS" << EOF
sdk.dir=$(android-sdk-root 2>/dev/null || echo "/path/to/android-sdk")
GOOGLE_API_KEY=$GOOGLE_API_KEY
OPENROUTER_API_KEY=$OPENROUTER_API_KEY
EOF

echo "✅ local.properties generated at: $LOCAL_PROPS"
