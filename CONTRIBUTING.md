# Contributing to DeepChat

Thank you for your interest in contributing to DeepChat! This document provides guidelines and instructions for maintaining and contributing to this project.

## Repository Management

### Changing the Default Branch

If you need to change the default branch for this repository (for example, to set a specific Minecraft version branch as the main branch), you have two options:

#### Option 1: Using GitHub Web Interface

1. Navigate to the repository on GitHub: https://github.com/wh0oo/deepchat
2. Click on **Settings** (you need admin/maintainer permissions)
3. In the left sidebar, click on **Branches** (under "Code and automation")
4. In the "Default branch" section, you'll see the current default branch
5. Click the ⇄ switch icon (or "Switch to another branch" button)
6. Select the branch you want to make the default from the dropdown menu
   - For example: `1.21`, `1.21.9`, `1.21.11`, or `mc-1.21.6`
7. Click **Update** to confirm
8. GitHub will show a warning about the impact - click **I understand, update the default branch**

**Note:** Changing the default branch affects:
- Which branch new pull requests target by default
- Which branch is shown when someone visits the repository
- Which branch is cloned by default with `git clone`

#### Option 2: Using GitHub CLI

If you have the [GitHub CLI](https://cli.github.com/) installed and authenticated:

```bash
# List all branches
gh api repos/wh0oo/deepchat/branches --jq '.[].name'

# Set the default branch (replace 'BRANCH_NAME' with your desired branch)
gh api repos/wh0oo/deepchat -X PATCH -f default_branch='BRANCH_NAME'
```

For example, to set `1.21.9` as the default:
```bash
gh api repos/wh0oo/deepchat -X PATCH -f default_branch='1.21.9'
```

### Branch Structure

This repository uses multiple branches for different Minecraft versions:
- `1.21` - For Minecraft 1.21
- `1.21.9` - For Minecraft 1.21.9
- `1.21.11` - For Minecraft 1.21.11
- `mc-1.21.6` - For Minecraft 1.21.6
- Other feature/development branches as needed

When selecting a default branch, consider:
- Which Minecraft version is most stable and widely used
- Which version has the most recent updates
- Which version you want users to see first when visiting the repository

## Development Guidelines

### Building the Project

This is a Fabric mod that requires:
- Java 21
- Gradle (included via wrapper)

To build:
```bash
./gradlew build
```

### Testing

After making changes, test the mod in a Minecraft instance with:
- Fabric Loader installed
- The appropriate Minecraft version
- The required Fabric API version

### Pull Requests

When submitting pull requests:
1. Target the appropriate branch for the Minecraft version you're working with
2. Ensure your changes don't break existing functionality
3. Update documentation if you're adding new features
4. Test thoroughly before submitting

## Questions?

If you have questions about contributing or repository management, please open an issue on GitHub.
