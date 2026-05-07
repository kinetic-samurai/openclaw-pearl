# Tool calling

OpenClaw Pearl should translate tool intent, not execute tools.

## OpenAI-style input

```json
{
  "model": "gemma-4-e2b-it",
  "messages": [
    { "role": "user", "content": "Read README.md" }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "read_file",
        "description": "Read a file from the workspace",
        "parameters": {
          "type": "object",
          "properties": {
            "path": { "type": "string" }
          },
          "required": ["path"]
        }
      }
    }
  ]
}
```

## OpenAI-style output

```json
{
  "choices": [
    {
      "finish_reason": "tool_calls",
      "message": {
        "role": "assistant",
        "content": null,
        "tool_calls": [
          {
            "id": "call_1",
            "type": "function",
            "function": {
              "name": "read_file",
              "arguments": "{\"path\":\"README.md\"}"
            }
          }
        ]
      }
    }
  ]
}
```

## Bridge rule

The bridge may validate JSON structure, but real permission checks belong in OpenClaw.
