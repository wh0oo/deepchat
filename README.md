# DeepChat
*A Fabric mod for integrating DeepSeek AI into Minecraft chat*
- Tested in mc-1.21.5-1.21.9 - mc-1.21.5-1.21.11 and mc-26.1.2 and 26.2

## Features
✅ **Implemented**
- `!ai <question>` command in chat
- 26.1.2 supports DeepSeek API (`deepseek-v4-pro` and `deepseek-v4-flash` models)
- Automatic config file generation
- Error handling with 3 retries

⚠️ **Limitations**
- API keys stored in plaintext (`config/deepchat/api_key.txt`)
- No rate limiting or cooldowns
- No encryption (planned for v2)

## Installation (Server side only)
1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Download the latest `.jar` from [DeepChat on Modrinth](https://modrinth.com/mod/deepchat)
3. Place in `mods/` folder

## Configuration
1. After first run, edit:
config/deepchat/
- api_key.txt # Your DeepSeek API key
-  model.txt # ~~"deepseek-chat"~~"deepseek-v4-pro" or ~~"deepseek-reasoner"~~ "deepseek-v4-flash"

