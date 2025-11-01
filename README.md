# DeepChat
*A Fabric mod for integrating DeepSeek AI into Minecraft chat*
- Tested in mc-1.21.5-1.21.9

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

## Installation (Server side only)
1. Install [Fabric Loader](https://fabricmc.net/use/)
2. As of the 25W44c snapshot [Fabric-language-kotlin](https://modrinth.com/mod/fabric-language-kotlin) is required.
3. Download the latest `.jar` from [DeepChat on Modrinth](https://modrinth.com/mod/deepchat)
4. Place in `mods/` folder

## Configuration
1. After first run, edit:
config/deepchat/
- api_key.txt # Your DeepSeek API key
-  model.txt # "deepseek-chat" or "deepseek-reasoner"

