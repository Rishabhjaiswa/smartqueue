import os
import re

files_to_update = [
    "/Users/Neel/GitHub/Smartqueue/backend/src/main/java/com/smartqueue/backend/service/DoctorQueueService.java",
    "/Users/Neel/GitHub/Smartqueue/backend/src/main/java/com/smartqueue/backend/service/QueueService.java",
    "/Users/Neel/GitHub/Smartqueue/backend/src/main/java/com/smartqueue/backend/service/WebSocketBroadcastService.java",
    "/Users/Neel/GitHub/Smartqueue/backend/src/main/java/com/smartqueue/backend/service/ClinicScheduledJobs.java",
    "/Users/Neel/GitHub/Smartqueue/backend/src/main/java/com/smartqueue/backend/service/ReceptionService.java",
    "/Users/Neel/GitHub/Smartqueue/backend/src/main/java/com/smartqueue/backend/idempotency/IdempotencyService.java",
    "/Users/Neel/GitHub/Smartqueue/backend/src/main/java/com/smartqueue/backend/controller/TelegramWebhookController.java"
]

for file_path in files_to_update:
    if not os.path.exists(file_path):
        continue
        
    with open(file_path, "r") as f:
        content = f.read()
        
    # 1. Add import java.util.Optional; if not exists
    if "import java.util.Optional;" not in content:
        content = re.sub(r'(import [^;]+;)(?![\s\S]*import)', r'\1\nimport java.util.Optional;', content)
        
    # 2. Replace @Autowired(required=false) private RedisTemplate...
    # with private final Optional<RedisTemplate<...>>
    content = re.sub(
        r'@org\.springframework\.beans\.factory\.annotation\.Autowired\(required\s*=\s*false\)\s*private\s+RedisTemplate<([^>]+)>\s+redisTemplate;',
        r'private final Optional<RedisTemplate<\1>> redisTemplate;',
        content
    )
    
    # 3. Replace redisTemplate == null with redisTemplate.isEmpty()
    content = content.replace("redisTemplate == null", "redisTemplate.isEmpty()")
    
    # 4. Replace redisTemplate.opsFor with redisTemplate.get().opsFor
    content = content.replace("redisTemplate.opsFor", "redisTemplate.get().opsFor")
    
    # 5. Replace redisTemplate.keys with redisTemplate.get().keys
    content = content.replace("redisTemplate.keys", "redisTemplate.get().keys")
    content = content.replace("redisTemplate.delete", "redisTemplate.get().delete")
    content = content.replace("redisTemplate.convertAndSend", "redisTemplate.get().convertAndSend")
    content = content.replace("redisTemplate.hasKey", "redisTemplate.get().hasKey")
    
    with open(file_path, "w") as f:
        f.write(content)
        
print("Updated all files!")
