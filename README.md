# DeepChat
*A Fabric proof of concept mod for integrating DeepSeek AI into Minecraft chat*

## Features
✅ **Implemented**
- `!ai <question>` command in chat
- Supports DeepSeek API (`deepseek-chat` and `deepseek-reasoner` models)
- Automatic config file generation
- Error handling with 3 retries

⚠️ **Limitations**
- API keys stored in plaintext (`config/deepchat/api_key.txt`)
- No rate limiting or cooldowns
- No encryption (planned for v2)

## Installation
1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Download the latest `.jar` from [Releases]()
3. Place in `mods/` folder

## Configuration
1. After first run, edit:
config/deepchat/
- api_key.txt # Your DeepSeek API key
-  model.txt # "deepseek-chat" or "deepseek-reasoner"

