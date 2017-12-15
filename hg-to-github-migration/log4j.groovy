log4j {
    //
    appender.stdout = "org.apache.log4j.ConsoleAppender"
    appender."stdout.layout"="org.apache.log4j.PatternLayout"
    //
    appender.scrlog = "org.apache.log4j.DailyRollingFileAppender"
    appender."scrlog.DatePattern"="'.'yyyy-MM-dd"
    appender."scrlog.Append"="true"
    appender."scrlog.File"="rootscript.log"
    appender."scrlog.layout"="org.apache.log4j.PatternLayout"
    appender."scrlog.layout.ConversionPattern"="%d %5p - %m%n"

    rootLogger="debug,scrlog,stdout"
    logger.ProcessLogger="debug,scrlog"
}