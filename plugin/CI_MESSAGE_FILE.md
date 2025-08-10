# CI_MESSAGE File Feature

## Overview

This plugin now saves the `CI_MESSAGE` to a file instead of using environment variables to avoid Linux environment size limitations.

## How It Works

### What Changes:
- **CI_MESSAGE** is now saved to a file called `.ci_message.txt` in the workspace
- All other CI variables continue to work as environment variables normally
- A new environment variable `CI_MESSAGE_FILE` points to the file location

### File Location:
```
workspace/.ci_message.txt
```

### Environment Variables:
- **`CI_MESSAGE_FILE`**: Contains the full path to the `.ci_message.txt` file
- **All other variables**: `CI_STATUS`, `BUILD_STATUS`, etc. continue as normal environment variables

## Usage Examples

### In Jenkins Pipeline:
```groovy
pipeline {
    stages {
        stage('Process Message') {
            steps {
                script {
                    // Read CI_MESSAGE from file
                    def ciMessage = readFile("${CI_MESSAGE_FILE}")
                    echo "Received message: ${ciMessage}"
                    
                    // Other CI variables work normally
                    echo "CI Status: ${CI_STATUS}"
                    echo "Build Status: ${BUILD_STATUS}"
                }
            }
        }
    }
}
```

### In Shell Scripts:
```bash
# Read CI_MESSAGE from file
CI_MESSAGE=$(cat "$CI_MESSAGE_FILE")
echo "Message content: $CI_MESSAGE"

# Other variables work normally
echo "CI Status: $CI_STATUS"
echo "Build Status: $BUILD_STATUS"
```

### Finding the File:
```bash
# Find CI_MESSAGE files in Jenkins workspaces
find /var/lib/jenkins/workspace -name ".ci_message.txt" -type f

# Show file contents and location
find /var/lib/jenkins/workspace -name ".ci_message.txt" -exec echo "=== {} ===" \; -exec cat {} \; -exec echo "" \;
```

## Fallback Behavior

If file writing fails for any reason:
- The plugin automatically falls back to using the `CI_MESSAGE` environment variable
- Error is logged but build continues normally
- No impact on other CI variables

## Benefits

1. **No Linux Environment Size Limits**: Large CI_MESSAGE content won't cause environment variable size issues
2. **Minimal Changes**: Only CI_MESSAGE is affected, all other variables work as before
3. **Robust Fallback**: Automatically falls back to environment variables if file operations fail
4. **Cross-Platform**: Works on both Linux and Windows Jenkins agents

## File Behavior

- **File is overwritten** with each new CI_MESSAGE (no accumulation)
- **File persists** until the workspace is cleaned or the next message arrives
- **File location** is available via `CI_MESSAGE_FILE` environment variable
- **File encoding** is UTF-8

This is a minimal change that specifically addresses the Linux environment variable size limitation for CI_MESSAGE while keeping the rest of the plugin functionality unchanged.

## Detailed Logging

The plugin now provides comprehensive logging to track the file save process. You'll see detailed log entries in the Jenkins build console showing each step:

### Log Entry Examples

#### Successful File Save Process:
```
INFO: Processing 3 message parameters for environment variables
INFO: Processing CI_MESSAGE parameter - routing to file instead of environment variable (size: 2048 characters)
INFO: === Starting CI_MESSAGE file save process ===
INFO: CI_MESSAGE content size: 2048 characters
INFO: Run type: WorkflowRun
INFO: Step 1: Determining workspace location...
INFO: Run is not AbstractBuild (likely Pipeline) - attempting alternative workspace discovery
INFO: Created workspace path from build root: /var/lib/jenkins/workspace/my-job/workspace
INFO: Step 2: Creating file path for CI_MESSAGE...
INFO: Target file path: /var/lib/jenkins/workspace/my-job/.ci_message.txt
INFO: Step 3: Writing CI_MESSAGE content to file...
INFO: File encoding: UTF-8
INFO: Successfully wrote 2048 characters to file
INFO: Step 4: Setting CI_MESSAGE_FILE environment variable...
INFO: Environment variable CI_MESSAGE_FILE set to: /var/lib/jenkins/workspace/my-job/.ci_message.txt
INFO: === CI_MESSAGE file save process completed successfully ===
INFO: File verification: File exists with size 2048 bytes
INFO: Environment variable processing completed
```

#### Fallback to Environment Variable (when file save fails):
```
INFO: Processing CI_MESSAGE parameter - routing to file instead of environment variable (size: 1024 characters)
INFO: === Starting CI_MESSAGE file save process ===
WARNING: Step 1 FAILED: Could not determine workspace location
INFO: FALLBACK: Using CI_MESSAGE environment variable instead of file
INFO: Set CI_MESSAGE environment variable (size: 1024 characters)
WARNING: === CI_MESSAGE file save process failed - used environment variable fallback ===
```

#### Exception Handling:
```
INFO: === Starting CI_MESSAGE file save process ===
SEVERE: EXCEPTION during CI_MESSAGE file save process: IOException: Permission denied
INFO: FALLBACK: Using CI_MESSAGE environment variable due to exception
INFO: Set CI_MESSAGE environment variable (size: 512 characters)
WARNING: === CI_MESSAGE file save process failed with exception - used environment variable fallback ===
```

### Log Levels Used:
- **INFO**: Normal operation steps and status updates
- **WARNING**: Non-critical issues and fallback scenarios  
- **SEVERE**: Critical errors that prevent file operations
- **FINE**: Detailed variable processing (other CI variables)

### What You'll See:
- **Message count and sizes** for all parameters being processed
- **Step-by-step file operations** with paths and results
- **Workspace detection methods** (AbstractBuild vs Pipeline)
- **File verification** after writing (existence and size checks)
- **Clear success/failure indicators** with detailed reasoning
- **Fallback behavior** when file operations fail

This comprehensive logging helps you troubleshoot any issues with the file-based CI_MESSAGE storage and understand exactly how the plugin is handling your messages.

## waitForCIMessage Logging

The plugin now also provides detailed logging for the `waitForCIMessage` pipeline step itself, so you'll see comprehensive logs during message reception:

### waitForCIMessage Log Examples

#### Successful Message Reception:
```
INFO: === Starting waitForCIMessage process ===
INFO: Provider: my-activemq, Topic: ci.messages, Broker: tcp://broker:61616
INFO: Selector: CI_TYPE = 'code-quality-checks-done'
INFO: Timeout: 15 minutes
INFO: Step 1: Creating connection to broker...
Step 1: Connecting to broker: tcp://broker:61616
INFO: Successfully connected to broker with client ID: 192.168.1.100_abc123-def456
INFO: Step 2: Creating JMS session and consumer...
INFO: Using TOPIC destination: ci.messages (durable subscriber)
Using TOPIC: ci.messages (durable)
INFO: Consumer created successfully
INFO: Step 3: Starting message reception loop...
Step 3: Listening for messages (timeout: 15 minutes)...
INFO: Message reception attempt #1
INFO: Message received! Processing message...
Message received! Processing...
INFO: Message body extracted (size: 2048 characters)
INFO: Step 4: Verifying message against checks...
INFO: Message verification PASSED - processing CI variables
Message verification PASSED
INFO: Setting CI variable: CI_MESSAGE (size: 2048 characters)
Setting CI variable: CI_MESSAGE
INFO: Step 5: Adding CIEnvironmentContributingAction to build
INFO: CIEnvironmentContributingAction added successfully
INFO: === waitForCIMessage completed successfully ===
=== Message processing completed successfully ===
INFO: Step 6: Cleaning up JMS resources...
Cleaning up JMS connection...
INFO: JMS consumer closed successfully
INFO: JMS connection closed successfully
INFO: === waitForCIMessage cleanup completed ===
```

#### Message Timeout:
```
INFO: === Starting waitForCIMessage process ===
INFO: Step 3: Starting message reception loop...
Step 3: Listening for messages (timeout: 5 minutes)...
INFO: Message reception attempt #1
INFO: No message received in attempt #1 - checking timeout...
WARNING: === waitForCIMessage TIMEOUT ===
WARNING: No valid message received after 300 seconds (1 attempts)
=== waitForCIMessage TIMEOUT ===
No valid message received after 300 seconds
INFO: Step 6: Cleaning up JMS resources...
```

#### Configuration Error:
```
SEVERE: === waitForCIMessage CONFIGURATION ERROR ===
SEVERE: One or more required configurations is invalid (null):
SEVERE: - IP address: 192.168.1.100
SEVERE: - Authentication method: configured
SEVERE: - Topic: NULL
SEVERE: - Broker: tcp://broker:61616
=== waitForCIMessage CONFIGURATION ERROR ===
Invalid configuration - check provider settings
```

### Complete Logging Flow

Now you get **two levels of detailed logging**:

1. **Message Reception** (`waitForCIMessage` step):
   - Connection setup and broker details
   - Message waiting and reception status
   - Message verification and processing
   - Resource cleanup

2. **Environment Variable Processing** (when build runs):
   - File writing operations for CI_MESSAGE
   - Directory creation and file paths
   - Success/failure status with fallbacks
   - File verification after writing

This gives you complete visibility into both the message reception process AND the file-based storage of CI_MESSAGE!

## Issue Resolution: Missing Environment Variable Processing

### The Problem

Originally, when using `waitForCIMessage` without explicitly specifying a variable name, the plugin would:

1. ✅ Successfully receive and process messages
2. ✅ Show detailed waitForCIMessage logs  
3. ❌ **NOT** create any environment variables or files

This happened because the `waitForCIMessage` step wasn't setting a variable name, so `pd.getVariable()` returned null, and no `CIEnvironmentContributingAction` was created.

### The Solution

Fixed in version 1.1.28: The plugin now **automatically defaults to `CI_MESSAGE`** when no variable name is specified:

```java
// Use specified variable name or default to CI_MESSAGE
String variableName = pd.getVariable();
if (StringUtils.isEmpty(variableName)) {
    variableName = ActiveMQSubscriberProviderData.DEFAULT_VARIABLE_NAME; // "CI_MESSAGE"
    log.info("No variable name specified, using default: " + variableName);
}
```

### Expected Behavior Now

When using `waitForCIMessage`, you'll see **both** sets of logs:

1. **Message Reception Logs:**
```
=== Starting waitForCIMessage process ===
Message received! Processing...
No variable name specified, using default: CI_MESSAGE
Setting CI variable: CI_MESSAGE
CIEnvironmentContributingAction added successfully
```

2. **File Processing Logs (later in build):**
```
=== CIEnvironmentContributingAction CREATED ===
Variable: CI_MESSAGE (size: 2048 characters)
=== buildEnvironment() called ===
Processing CI_MESSAGE parameter - routing to file instead of environment variable
=== Starting CI_MESSAGE file save process ===
Successfully wrote 2048 characters to file
```

Now you should see the complete flow from message reception to file storage! 🎉