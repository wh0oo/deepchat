[![build](https://github.com/wh0oo/deepchat/actions/workflows/build.yml/badge.svg?branch=1.25-debug)](https://github.com/wh0oo/deepchat/actions/workflows/build.yml)

# DeepChat
*A Fabric proof of concept mod for integrating DeepSeek AI into Minecraft chat*

🔥 See it in action:
Join **The Netherhood** at  
**`netherhood.blockworlds.io`**  
or direct IP: **`76.164.199.69:25565`**

## Features ## 

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
2. Download the latest `.jar` from [DeepChat on Modrinth](https://modrinth.com/mod/deepchat)
3. Place in `mods/` folder

## Configuration
1. After first run, edit:
config/deepchat/
- api_key.txt # Your DeepSeek API key
-  model.txt # "deepseek-chat" or "deepseek-reasoner"

This readme was created by DeepSeek

