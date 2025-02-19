{-# LANGUAGE NamedFieldPuns #-}

module Main where

import Control.Concurrent (threadDelay)
import Data.Time.Clock (getCurrentTime)
import Data.Time.LocalTime (getCurrentTimeZone)
import Server
import Simplex.Chat.Controller (versionNumber, versionString)
import Simplex.Chat.Core
import Simplex.Chat.Options
import Simplex.Chat.Terminal
import Simplex.Chat.View (serializeChatResponse)
import Simplex.Messaging.Client (NetworkConfig (..))
import System.Directory (getAppUserDataDirectory)
import System.Terminal (withTerminal)

main :: IO ()
main = do
  appDir <- getAppUserDataDirectory "simplex"
  opts@ChatOpts {chatCmd, chatServerPort} <- getChatOpts appDir "simplex_v1"
  if null chatCmd
    then case chatServerPort of
      Just chatPort ->
        simplexChatServer defaultChatServerConfig {chatPort} terminalChatConfig opts
      _ -> do
        welcome opts
        t <- withTerminal pure
        simplexChatTerminal terminalChatConfig opts t
    else simplexChatCore terminalChatConfig opts Nothing $ \user cc -> do
      r <- sendChatCmdStr cc chatCmd
      ts <- getCurrentTime
      tz <- getCurrentTimeZone
      putStrLn $ serializeChatResponse (Just user) ts tz r
      threadDelay $ chatCmdDelay opts * 1000000

welcome :: ChatOpts -> IO ()
welcome ChatOpts {coreOptions = CoreChatOpts {dbFilePrefix, networkConfig}} =
  mapM_
    putStrLn
    [ versionString versionNumber,
      "db: " <> dbFilePrefix <> "_chat.db, " <> dbFilePrefix <> "_agent.db",
      maybe
        "direct network connection - use `/network` command or `-x` CLI option to connect via SOCKS5 at :9050"
        (("using SOCKS5 proxy " <>) . show)
        (socksProxy networkConfig),
      "type \"/help\" or \"/h\" for usage info"
    ]
