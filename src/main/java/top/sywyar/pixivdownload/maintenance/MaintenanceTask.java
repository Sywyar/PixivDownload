package top.sywyar.pixivdownload.maintenance;

/**
 * 维护任务接口。每周维护窗口由 {@link MaintenanceCoordinator} 顺序遍历所有 Bean 化实现执行。
 *
 * <p>实现要点：
 * <ul>
 *   <li>{@link #name()} 用于日志辨识，固定且稳定。</li>
 *   <li>{@link #execute(MaintenanceContext)} 内部异常应自行吞掉/记录；协调器会捕获并继续后续任务，
 *       但抛出的异常会被记录为本任务失败。</li>
 *   <li>顺序由 Spring 的 {@code @Order} 决定；未标注则使用 Spring 默认顺序。</li>
 *   <li>新增维护任务请实现本接口并注册为 Bean，不要绕过协调器自行写新的 {@code @Scheduled} 清理。</li>
 * </ul>
 */
public interface MaintenanceTask {

    String name();

    void execute(MaintenanceContext context) throws Exception;
}
