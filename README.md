# Cukes

This repository is a fork of https://github.com/hibernate/hibernate-reactive.

The reason for the fork is that [there is a bug in hibernate-core 6.2.x][1] when used with hibernate-reactive, 
which is fixed in 6.3.x but the merge of hibernate-core 6.3.x in Micronaut 4 [is blocked by hibernate-reactive 2.1.0][2].
The bug originates from a change in hibernate-core 6.2, which changes Java enums to be stored as DB enums, instead of varchars.

This fork adds the least invasive [workaround][3] for the bug. This workaround happens to be in hibernate-reactive and not hibernate-core.

### Publishing maven artifacts

The forked hibernate-reactive artifacts are published by using [Github Pages][4].

#### Uploading instructions

1. clone this repo

    ```shell
    git clone git@github.com:DND-IT/hibernate-reactive.git
    ```

2. compile the project

   ```shell
   ./gradlew compileJava
   ```

3. Build a local directory structure for the maven repository that will be uploaded to Github Pages.

    ```shell
    ./gradlew -Dmaven.repo.local=`pwd`/build/mvn-repo publishToMavenLocal
    ```

4. Upload the maven repository to Github Pages.

    ```shell
    ./gradlew gitPublishPush
    ```

5. Configure the dependent projects/modules:

    ```xml
    <repository>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
        <id>dnd-it-hibernate-reactive</id>
        <url>https://dnd-it.github.io/hibernate-reactive/</url>
    </repository>
    ```

   - Add the dependency:

       ```xml
       <dependencyManagement>
           <dependencies>
               <dependency>
                   <groupId>org.hibernate.reactive</groupId>
                   <artifactId>hibernate-reactive-core</artifactId>
                   <version>CUSTOM_VERSION</version>
               </dependency>
           </dependencies>
       </dependencyManagement>
       ```

[1]:https://hibernate.atlassian.net/browse/HHH-17180
[2]:https://github.com/micronaut-projects/micronaut-sql/pull/1080#issuecomment-1727933815
[3]:https://github.com/DND-IT/hibernate-reactive/commit/ba24a7fe1b333639976fc70582dda00d158a82f2
[4]:https://docs.github.com/en/pages/getting-started-with-github-pages/about-github-pages
