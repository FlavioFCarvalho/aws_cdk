package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

public class Service01Stack extends Stack {
    public Service01Stack(final Construct scope, final String id, Cluster cluster) {
        this(scope, id, null, cluster);
    }

    public Service01Stack(final Construct scope, final String id, final StackProps props, Cluster cluster) {
        super(scope, id, props);

        ApplicationLoadBalancedFargateService service01 = ApplicationLoadBalancedFargateService
                .Builder
                .create(this, "ALB01")
                .serviceName("service_01")
                .cluster(cluster)
                .cpu(512)
                .memoryLimitMiB(1024)
                .desiredCount(2)
                .listenerPort(8080)
                .taskImageOptions( // Definição da task (contendo a imagem da aplicação rodando em um container desponibilizando os logs)
                        ApplicationLoadBalancedTaskImageOptions.builder() // Especificação do container e da image
                                .containerName("aws_projeto01_container")
                                .image(ContainerImage.fromRegistry("flaviocfr/curso_aws_project01:1.0.0"))
                                .containerPort(8080)
                                .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder() // Expecificando os Logs
                                        .logGroup(LogGroup.Builder.create(this, "Service01LogGroup")
                                                .logGroupName("Service01")
                                                .removalPolicy(RemovalPolicy.DESTROY)
                                                .build())
                                        .streamPrefix("Service01")
                                        .build()))
                                       .build())
                                      .publicLoadBalancer(true)
                                  .build();

        // Configuração do TargetGroup. Responsável em verificar se a aplicação está UP ou não.
        service01.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                .path("/actuator/health")
                .port("8080")
                .healthyHttpCodes("200")
                .build());

        // Configuração da AutoScale: Monitora as requisições da aplicação
        ScalableTaskCount scalableTaskCount = service01.getService().autoScaleTaskCount(EnableScalingProps.builder()
                .minCapacity(2) // número mínimo de instancia da aplicação
                .maxCapacity(4) // número máximo de instancia da aplicação
                .build());

        scalableTaskCount.scaleOnCpuUtilization("Service01AutoScaling", CpuUtilizationScalingProps.builder()
                .targetUtilizationPercent(50) // Percentagem de consumo da aplicação usado como parametro para destruir ou construir uma instancia da aplicação
                .scaleInCooldown(Duration.seconds(60)) // tempo necessário para construir nova instancia da aplicação
                .scaleOutCooldown(Duration.seconds(60)) // tempo necessário para destruir uma instancia da aplicação
                .build());
    }
}
