Project Overview & Features

This is a comprehensive Discord bot that automatically fetches, parses, and displays League of Legends patch notes in an organized, interactive format. The bot transforms complex patch note websites into clean, digestible Discord messages with intelligent content classification.

Core Features
Slash Commands:

/latestpatch - Displays the most recent patch with categorized champion changes
/subscribe - Subscribe a Discord channel to receive automatic patch notifications
/unsubscribe - Remove channel from patch notification list
/ping - Check bot latency and responsiveness
/info - Display bot information and statistics
/echo [text] - Echo back user input (utility command)

Interactive Features:

Smart Champion Classification: Automatically categorizes champions as buffed, nerfed, or adjusted based on stat analysis
Interactive Detail Buttons: Click-to-reveal detailed changes without cluttering chat
Ephemeral responses: Detailed information appears privately to the requesting user
Automatic Notifications: Scheduled monitoring sends patch alerts to subscribed channels
Game Mode Filtering: Excludes ARAM, Arena, rotating game modes, and event content

Advanced Functionality:

Intelligent Buff/Nerf Detection: Analyzes stat arrows (68 → 63), ability scaling changes, and contextual language to classify changes
Multi-Strategy Content Extraction: Uses structured parsing, pattern matching, and context analysis as fallbacks
Content Validation: Filters out cosmetic changes, game modes, and irrelevant content
Message Splitting: Handles Discord's 2000 character limit by intelligently breaking long content

Technical Stack
Core Technologies:

Programming Language: Java 17 with modern features
Build Tool: Maven for dependency management and build automation
Database: MySQL with HikariCP connection pooling for optimal performance
Discord Integration: JDA (Java Discord API) for bot functionality
HTTP Client: OkHttp3 for reliable external API requests
HTML Parsing: JSoup for web scraping Riot's patch notes
JSON Processing: Gson for Riot API data serialization
Logging: SLF4J with Logback for comprehensive logging
Configuration: Properties files for secure credential management

External APIs:

Riot Data Dragon API: For patch version information and champion metadata
Riot Patch Notes Website: Scraped for detailed patch content
Discord API: For bot commands, interactions, and notifications

Architecture Details
Component Breakdown
Core Services:

RiotApiService: Coordinates API calls and web scraping with multiple fallback strategies
DatabaseManager: Handles MySQL operations with connection pooling and transaction management
PatchNotificationTask: Scheduled service for automatic patch monitoring

Specialized Content Extractors:

ChampionExtractor: Advanced pattern matching for champion balance changes with game mode filtering
ItemExtractor: Identifies item changes and stat modifications
BugFixExtractor: Parses bug fixes with relevance filtering and deduplication

User Interface Components:

CommandListener: Routes slash commands to appropriate handlers
ButtonInteractionHandler: Manages interactive button responses with content caching
LatestPatchCommand: Primary command with intelligent content presentation

Database Schema
patches table:

Stores basic patch information - version, title, release date, and URL.
subscribed_channels table:
Tracks which Discord channels want patch notifications.
patch_notifications table:
Records which notifications were sent to prevent duplicates.

Performance Optimizations
Connection Management:

HikariCP connection pooling with optimized settings
HTTP connection reuse with OkHttp3's built-in pooling
Proper resource cleanup with try-with-resources patterns

Content Processing:

Multi-threaded extraction with executor services
Content caching to reduce redundant API calls
Efficient CSS selectors for DOM parsing
Lazy loading of non-critical content

Discord Integration:

Deferred responses to prevent timeout issues
Message splitting with intelligent break points
Ephemeral responses to reduce server clutter
Button interaction caching for responsive UX

Deployment Information
System Requirements
Minimum Hardware:

CPU: 2 cores, 2.0 GHz
RAM: 1 GB available memory
Storage: 500 MB for application and logs
Network: Stable internet connection for API access

Software Requirements:

Java Runtime: OpenJDK 17 or higher
Database: MySQL 8.0+ or MariaDB 10.3+
Operating System: Linux (Ubuntu 20.04+), Windows 10+, or macOS 11+

Security Considerations
Credential Management:

Discord bot token stored in encrypted configuration files
Database credentials separated from source code
Environment-specific configuration profiles
No hardcoded secrets in the codebase

Network Security:

HTTPS-only external API communication
Database connections over secure channels
Rate limiting for external API requests
Input validation for all user-provided data

Access Control:

Database user with minimal required permissions
Discord bot permissions limited to necessary functions
Channel-based access control for notifications
Audit logging for administrative actions

<img width="1231" height="954" alt="image" src="https://github.com/user-attachments/assets/e92b8a89-7da6-43e7-ab51-8d4bf0a3a341" />

<img width="1214" height="910" alt="image" src="https://github.com/user-attachments/assets/8292668b-7634-4552-bc68-98a64941211e" />

<img width="1188" height="423" alt="image" src="https://github.com/user-attachments/assets/70476ab2-138d-4627-8d1a-972d00c7cf2f" />

<img width="1058" height="237" alt="image" src="https://github.com/user-attachments/assets/738d759d-ddf1-495e-a0c3-fad23b82c2ed" />


