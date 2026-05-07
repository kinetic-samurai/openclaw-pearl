#!/bin/bash
# Test 1: Proper SSE Streaming
# Validates:
# - Content-Type is text/event-stream
# - Multiple data: lines with actual content chunks (not just one big chunk)
# - Each data: line is valid JSON with chat.completion.chunk structure
# - Final data: [DONE] is received
# - Streamed content assembles into expected full response

set -e

BRIDGE_URL="http://127.0.0.1:8765"
# Use a prompt that should generate a multi-word response to test real streaming
PROMPT="Count from 1 to 5, each number on its own line"
TMP_FILE=$(mktemp)
HDR_FILE=$(mktemp)

echo "=== Test 1: Proper SSE Streaming ==="
echo "Sending streaming request..."

# Send request, capture both headers and body
curl -s -D "$HDR_FILE" -X POST "$BRIDGE_URL/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"gemma-4-e2b-it\",\"messages\":[{\"role\":\"user\",\"content\":\"$PROMPT\"}],\"stream\":true}" \
  > "$TMP_FILE" 2>&1 || true

# Check 1: Content-Type
CONTENT_TYPE=$(grep -i "content-type" "$HDR_FILE" | head -1 | tr -d '\r\n')
echo "Content-Type: $CONTENT_TYPE"
if echo "$CONTENT_TYPE" | grep -qi "text/event-stream"; then
  echo "PASS: Content-Type is text/event-stream"
else
  echo "FAIL: Content-Type is not text/event-stream (got: $CONTENT_TYPE)"
  rm -f "$TMP_FILE" "$HDR_FILE"
  exit 1
fi

# Check 2: Count data: lines with actual content (not [DONE])
BODY=$(cat "$TMP_FILE")
CONTENT_CHUNKS=$(echo "$BODY" | grep "^data:" | grep -v "\[DONE\]" | wc -l)
echo "Number of content data: lines: $CONTENT_CHUNKS"

# Real streaming should produce multiple chunks. The current pseudo-streaming
# produces exactly 1 content chunk with the full response. We need > 1.
if [ "$CONTENT_CHUNKS" -gt 1 ]; then
  echo "PASS: Received multiple content chunks (real streaming)"
else
  echo "FAIL: Expected multiple content chunks, got $CONTENT_CHUNKS (pseudo-streaming detected - full response in single chunk)"
  echo "Body was:"
  cat "$TMP_FILE"
  rm -f "$TMP_FILE" "$HDR_FILE"
  exit 1
fi

# Check 3: Each data: line is valid JSON with chat.completion.chunk structure
PASS_JSON=true
while IFS= read -r line; do
  if echo "$line" | grep -q "^data: \[DONE\]"; then
    continue
  fi
  JSON_PART=$(echo "$line" | sed 's/^data: //' | tr -d '\r')
  if ! echo "$JSON_PART" | python3 -c "import sys,json; d=json.load(sys.stdin); assert d.get('object')=='chat.completion.chunk','Missing chat.completion.chunk object'" 2>/dev/null; then
    echo "FAIL: Invalid JSON or missing chat.completion.chunk in: $JSON_PART"
    PASS_JSON=false
    break
  fi
done < <(echo "$BODY" | grep "^data:")

if $PASS_JSON; then
  echo "PASS: All data: lines are valid chat.completion.chunk JSON"
else
  rm -f "$TMP_FILE" "$HDR_FILE"
  exit 1
fi

# Check 4: Final data: [DONE]
if echo "$BODY" | grep -q "data: \[DONE\]"; then
  echo "PASS: Received data: [DONE]"
else
  echo "FAIL: No data: [DONE] received"
  rm -f "$TMP_FILE" "$HDR_FILE"
  exit 1
fi

# Check 5: Streamed content assembles correctly (non-empty)
ASSEMBLED=$(echo "$BODY" | grep "^data:" | grep -v "\[DONE\]" | \
  python3 -c "
import sys, json
full_content = ''
for line in sys.stdin:
    data = line.strip().replace('data: ','')
    if data == '[DONE]':
        continue
    try:
        chunk = json.loads(data)
        delta = chunk.get('choices',[{}])[0].get('delta',{})
        content = delta.get('content','')
        if content:
            full_content += content
    except:
        pass
print(full_content)
" || true)

if [ -n "$ASSEMBLED" ] && [ "${#ASSEMBLED}" -gt 1 ]; then
  echo "PASS: Assembled content is non-empty (${#ASSEMBLED} chars)"
else
  echo "FAIL: Assembled content is empty or too short: '$ASSEMBLED'"
  rm -f "$TMP_FILE" "$HDR_FILE"
  exit 1
fi

echo "=== TEST 1 PASSED ==="
rm -f "$TMP_FILE" "$HDR_FILE"
