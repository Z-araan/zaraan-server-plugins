#!/usr/bin/env bash
# zaraan星火之域 插件构建脚本
set -e
cd "$(dirname "$0")"

PAPER_API="${PAPER_API:-/tmp/paper-api-26.2.jar}"
SERVER_LIB="${SERVER_LIB:-/opt/paperMC/libraries}"
PLUGINS="${PLUGINS:-/opt/paperMC/plugins}"

libs() {
  local out=""
  for p in "$@"; do
    local j
    j=$(find "$SERVER_LIB" -path "*$p*" -name "*.jar" 2>/dev/null | sort | tail -1)
    [ -n "$j" ] && out="$out:$j"
  done
  echo "$out"
}

CP="$PAPER_API:$PLUGINS/Vault.jar:$PLUGINS/PlaceholderAPI-2.12.3.jar"
CP="$CP$(libs adventure-api adventure-key adventure-text-serializer-plain adventure-text-serializer-legacy examination-api annotations log4j-api)"
CP="$CP:$(find "$SERVER_LIB" -name 'guava-*.jar' | sort | tail -1)"
CP="$CP:$(find "$SERVER_LIB" -path '*bungeecord-chat*' -name '*.jar' | sort | tail -1)"

echo "==> 编译 ZaraanCore"
rm -rf ZaraanCore/build/classes && mkdir -p ZaraanCore/build/classes
javac --release 21 -encoding UTF-8 -cp "$CP" -d ZaraanCore/build/classes ZaraanCore/src/cn/zaraan/core/*.java
cp ZaraanCore/plugin.yml ZaraanCore/config.yml ZaraanCore/build/classes/
(cd ZaraanCore/build/classes && jar cf ../ZaraanCore.jar .)
echo "    -> ZaraanCore/build/ZaraanCore.jar"

echo "==> 编译 ZaraanAI"
rm -rf ZaraanAI/build/classes && mkdir -p ZaraanAI/build/classes
javac --release 21 -encoding UTF-8 -cp "$CP" -d ZaraanAI/build/classes ZaraanAI/src/cn/zaraan/ai/*.java
cp ZaraanAI/plugin.yml ZaraanAI/config.yml ZaraanAI/build/classes/
(cd ZaraanAI/build/classes && jar cf ../ZaraanAI.jar .)
echo "    -> ZaraanAI/build/ZaraanAI.jar"

echo "==> 全部完成"
