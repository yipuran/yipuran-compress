# yipuran-compress
#### Java tar and gzip compress library.<br/>
圧縮・展開する機能は yipuran-core の中に **ZipProcessor** を持っている。yipuran-core に入るべき機能ではあるが、**tar , gzip** での圧縮展開するために、 Apacheの commons-compress を使用しているので、依存で使用するＪＡＲサイズの量が結構大きくなってしまう。この為、分離したのが、**yipuran-compress** である。

## Dependency
https://github.com/yipuran/yipuran-core


## Document
Extract doc/yipuran-compress-doc.zip and see the Javadoc

## Setup pom.xml
```
<repositories>
   <repository>
      <id>yipuran-compress</id>
      <url>https://raw.github.com/yipuran/yipuran-compress/mvn-repo</url>
   </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.yipuran.compress</groupId>
        <artifactId>yipuran-compress</artifactId>
        <version>4.0</version>
    </dependency>
</dependencies>
```
